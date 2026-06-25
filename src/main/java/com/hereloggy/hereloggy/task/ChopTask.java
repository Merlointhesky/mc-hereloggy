package com.hereloggy.hereloggy.task;

import com.hereloggy.hereloggy.HereLoggyPlugin;
import com.hereloggy.hereloggy.auraskills.AuraSkillsHelper;
import com.hereloggy.hereloggy.hereroleplay.HereRolePlayHelper;
import com.hereloggy.hereloggy.config.TreeConfigManager;
import com.hereloggy.hereloggy.config.PlayerTreeConfig;
import com.hereloggy.hereloggy.config.TreeSettings;
import com.hereloggy.hereloggy.config.TreeConfigUI;
import com.hereloggy.hereloggy.map.ScanManager;
import com.hereloggy.hereloggy.map.ScanResult;
import com.hereloggy.hereloggy.path.PathGenerator;
import com.hereloggy.hereloggy.setup.SetupConfiguration;
import com.hereloggy.hereloggy.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ChopTask extends BukkitRunnable {

    private static final double SPEED = 0.22;
    private static final double SNAP_DISTANCE = 0.3;
    private static final double MAX_DIRECT_STEP_DISTANCE = 1.75;
    private static final int CHOP_PAUSE_TICKS = 8;
    private static final int STUCK_TICK_THRESHOLD = 12;

    private final HereLoggyPlugin plugin;
    private final Player player;
    private final List<Location> path;
    private final AuraSkillsHelper auraSkillsHelper;
    private final HereRolePlayHelper hereRolePlayHelper;
    private final ScanManager scanManager;
    private final SetupManager setupManager;
    private final TreeConfigManager configManager;
    private ScanResult scanResult;

    private int currentIndex = 0;
    private int chopPause = 0;
    private int stuckTicks = 0;
    private int foliageScanCooldown = 0;
    private int lastTargetIndex = -1;
    private double lastDist = Double.MAX_VALUE;
    private Location lastLocation = null;
    private int inactiveTicks = 0;

    // Breadcrumb and movement tracking for diagnostics
    private final Queue<Location> breadcrumbs = new LinkedList<>();
    private static final int BREADCRUMB_LIMIT = 20;
    private Location lastTeleportDest = null;
    private int teleportRetryCount = 0;

    private void updateBreadcrumbs(Location current) {
        breadcrumbs.add(current.clone());
        if (breadcrumbs.size() > BREADCRUMB_LIMIT) {
            breadcrumbs.poll();
        }
    }

    private double getBreadcrumbDisplacement() {
        if (breadcrumbs.size() < 2) return 0.0;
        Location oldest = breadcrumbs.peek();
        Location latest = player.getLocation();
        if (oldest.getWorld() != latest.getWorld()) return 999.0;
        return latest.distance(oldest);
    }

    // Track chopped tree coordinate keys so we do not chop multiple times
    private final Set<String> choppedTrees = new HashSet<>();
    // Track permanently protected tree bases that failed to break
    private final Set<String> protectedTrees = new HashSet<>();
    // Advanced Inactivity displacement tracking fields
    private Location baselineLocation = null;
    private int baselineTicks = 0;
    private int heartbeatTicks = 0;

    // Activity tracking stats
    private final Map<Material, Integer> collectedLogs = new HashMap<>();
    private final Map<Material, Integer> collectedFoliage = new HashMap<>();
    private int refuelsCount = 0;
    private int replantedCount = 0;

    public ChopTask(HereLoggyPlugin plugin, Player player, List<Location> path, 
                    AuraSkillsHelper auraSkillsHelper, HereRolePlayHelper hereRolePlayHelper, ScanResult scanResult) {
        this.plugin = plugin;
        this.player = player;
        this.path = path;
        this.auraSkillsHelper = auraSkillsHelper;
        this.hereRolePlayHelper = hereRolePlayHelper;
        this.scanManager = plugin.getScanManager();
        this.setupManager = plugin.getSetupManager();
        this.configManager = plugin.getTreeConfigManager();
        this.scanResult = scanResult;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public HereLoggyPlugin getPlugin() {
        return plugin;
    }


    @Override
    public synchronized void cancel() throws IllegalStateException {
        plugin.getChopTaskManager().removeActiveTask(player.getUniqueId());
        super.cancel();
        plugin.getChopTaskManager().startAutoDefense(player);
    }

    @Override
    public void run() {
        try {
            if (!player.isOnline()) {
                cancel();
                return;
            }

            Location current = player.getLocation();
            updateBreadcrumbs(current);

            // Periodic status heartbeat every 10 seconds (200 ticks)
            heartbeatTicks++;
            if (heartbeatTicks >= 200) {
                heartbeatTicks = 0;
            }

            // Track movement
            double movementDist = 0.0;
            if (lastLocation != null && lastLocation.getWorld() == current.getWorld()) {
                movementDist = current.distance(lastLocation);
            }

            // Run the core tick logic
            boolean wasActive = tick(current);

            // Advanced Inactivity Detection based on net displacement over 10 ticks (0.5 seconds)
            if (baselineLocation == null || baselineLocation.getWorld() != current.getWorld()) {
                baselineLocation = current.clone();
                baselineTicks = 0;
                inactiveTicks = 0;
            } else {
                baselineTicks++;
                if (baselineTicks >= 10) {
                    double netDisplacement = current.distance(baselineLocation);
                    
                    // If player hasn't made real progress (moved less than 0.5 blocks net over 0.5 seconds)
                    // and wasn't doing active work (chopping/feeding/defense)
                    if (netDisplacement < 0.5 && !wasActive && chopPause <= 0) {
                        inactiveTicks += 10;
                    } else {
                        inactiveTicks = 0;
                    }
                    
                    baselineLocation = current.clone();
                    baselineTicks = 0;
                }
            }

            // Check for inactivity threshold (20 ticks = 1.0 second of absolute freeze or lack of progress)
            if (inactiveTicks >= 20) {
                player.sendMessage(Component.text("⚠️ Inactivity detected! Force rescanning and bypassing block...").color(NamedTextColor.YELLOW));
                inactiveTicks = 0;
                stuckTicks = 0;

                if (!path.isEmpty()) {
                    currentIndex = (currentIndex + 1) % path.size();
                    Location nextTarget = path.get(currentIndex);
                    teleportToTarget(current, nextTarget);
                }

                triggerRescan();

                // Reset baseline to teleported location
                baselineLocation = player.getLocation();
                baselineTicks = 0;
            }

            lastLocation = player.getLocation();
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Unexpected error in HereLoggy ChopTask for player " + player.getName(), t);
            player.sendMessage(Component.text("⚠️ An unexpected error occurred during auto-chopping! Gracefully stopping task...").color(NamedTextColor.RED));
            try {
                cancel();
            } catch (Exception ex) {
                // Ignore if already cancelled
            }
        }
    }

    private boolean tick(Location current) {
        // 1a. Hostile mob defense
        if (handleDefense()) {
            chopPause = 8; // pause auto-chopping for 8 ticks to honor weapon cooldown
            return true;
        }

        // 1b. Saturation & Hunger feeding
        if (player.getFoodLevel() < 20) {
            handleFeeding();
            if (chopPause > 0) return true; // return if eating
        }

        // 1c. Inventory Full Check
        if (isInventoryFull()) {
            if (attemptDumpToChests()) {
                player.sendMessage(Component.text("Inventory cleared into deposit chests. Resuming...").color(NamedTextColor.GREEN));
                return true;
            } else {
                sendActivitySummary();
                cancel();
                plugin.getChopTaskManager().recordDurabilityStop(player, currentIndex);
                player.sendMessage(Component.text("Inventory full! Auto-chopping paused. Configure Keep/Trash chests, then use /hl restart to resume.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }

        if (path.isEmpty()) {
            cancel();
            return false;
        }

        if (chopPause > 0) {
            chopPause--;
            return true;
        }

        // Safe bound checks for currentIndex to prevent IndexOutOfBoundsException during asynchronous rescan callback
        if (currentIndex < 0 || currentIndex >= path.size()) {
            currentIndex = 0;
        }

        Location target = path.get(currentIndex);

        // 1d. Local Foliage Scan and Active Clearing
        if (foliageScanCooldown <= 0) {
            scanAndClearFoliage(current);
            foliageScanCooldown = 4; // Scan and clear every 5 ticks to optimize performance
        } else {
            foliageScanCooldown--;
        }

        if (current.getWorld() != target.getWorld()) {
            sendActivitySummary();
            cancel();
            player.sendMessage(Component.text("Auto-chopping stopped — you left the chopping area.")
                    .color(NamedTextColor.RED));
            return false;
        }

        // Check if player is stuck or fell
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (totalDist > MAX_DIRECT_STEP_DISTANCE) {
            teleportToTarget(current, target);
            stuckTicks = 0;
            return true;
        }

        // Prioritize: check if we are adjacent to a tree in our scan that has NOT been chopped yet
        if (checkAndChopAdjacentTrees(current)) {
            chopPause = CHOP_PAUSE_TICKS;
            return true;
        }

        // Collision checking
        if (currentIndex != lastTargetIndex) {
            lastTargetIndex = currentIndex;
            lastDist = totalDist;
            stuckTicks = 0;
        } else {
            if (totalDist >= lastDist - 0.02) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastDist = totalDist;
        }

        if (stuckTicks >= STUCK_TICK_THRESHOLD) {
            // Bypass stuck block and continue to the next path node
            currentIndex = (currentIndex + 1) % path.size();
            Location nextTarget = path.get(currentIndex);
            teleportToTarget(current, nextTarget);
            stuckTicks = 0;
            player.sendMessage(Component.text("Bypassed stuck coordinate and continuing to next path point...").color(NamedTextColor.YELLOW));
            return true;
        }

        // Snap to target if very close
        if (totalDist < SNAP_DISTANCE) {
            currentIndex++;

            if (currentIndex >= path.size()) {
                dumpSaplingsToChests();
                sendActivitySummary();
                player.sendMessage(Component.text("Auto-chopping loop completed! Checking for regrown trees...").color(NamedTextColor.GREEN));
                currentIndex = 0;
                triggerRescan();
            }
            return true;
        } else {
            // Apply velocity towards target
            Vector direction = new Vector(dx, dy, dz).normalize();
            double speedMultiplier = 1.0 + ((auraSkillsHelper != null ? auraSkillsHelper.getForagingLevel(player) : 0) * 0.01);
            Vector velocity = direction.multiply(SPEED * speedMultiplier);
            player.setVelocity(velocity);
        }

        return false;
    }

    private void teleportToTarget(Location current, Location target) {
        Location snap = target.clone();
        
        World world = snap.getWorld();
        int tx = snap.getBlockX();
        int tz = snap.getBlockZ();
        int targetY = snap.getBlockY();
        
        int safeY = -1;
        for (int dy = 0; dy <= 4; dy++) {
            int y1 = targetY + dy;
            if (isSafeStandLocation(world, tx, y1, tz)) {
                safeY = y1;
                break;
            }
            if (dy > 0) {
                int y2 = targetY - dy;
                if (isSafeStandLocation(world, tx, y2, tz)) {
                    safeY = y2;
                    break;
                }
            }
        }
        
        if (safeY != -1) {
            snap.setY(safeY);
            target.setY(safeY);
        } else {
            int highestY = world.getHighestBlockYAt(tx, tz);
            if (isSafeStandLocation(world, tx, highestY + 1, tz)) {
                snap.setY(highestY + 1);
                target.setY(highestY + 1);
            }
        }

        snap.setPitch(current.getPitch());
        snap.setYaw(current.getYaw());

        // Duplicate teleport loop detection
        if (lastTeleportDest != null && lastTeleportDest.getWorld() == snap.getWorld() && lastTeleportDest.distanceSquared(snap) < 0.01) {
            teleportRetryCount++;
            if (teleportRetryCount >= 3) {
                currentIndex = (currentIndex + 1) % path.size();
                teleportRetryCount = 0;
                return;
            }
        } else {
            lastTeleportDest = snap.clone();
            teleportRetryCount = 0;
        }

        player.teleport(snap);
    }

    private boolean checkAndChopAdjacentTrees(Location current) {
        if (scanResult == null) return false;

        World world = current.getWorld();
        int px = current.getBlockX();
        int pz = current.getBlockZ();

        // Search adjacent 3-block radius horizontally for a tree base in the world
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int tx = px + dx;
                int tz = pz + dz;

                int groundY = scanResult.getGroundY(tx, tz);
                Block baseBlock = world.getBlockAt(tx, groundY + 1, tz);

                String key = tx + "," + (groundY + 1) + "," + tz;
                if (choppedTrees.contains(key) || protectedTrees.contains(key)) {
                    continue;
                }

                if (isLogBlock(baseBlock.getType()) || isMangroveRootBlock(baseBlock.getType())) {
                    Material logType = PlayerTreeConfig.normalizeLogType(baseBlock.getType());
                    TreeSettings settings = configManager.getTreeSettings(player.getUniqueId(), logType);

                    if (settings.isEnabled()) {
                        // Begin chopping down this tree!
                        chopTree(baseBlock, logType, settings);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void chopTree(Block baseBlock, Material logType, TreeSettings settings) {
        player.sendActionBar(Component.text("🌳 Felling " + TreeConfigUI.getTreeDisplayName(logType) + "...").color(NamedTextColor.GREEN));

        PlayerTreeConfig config = configManager.getPlayerConfig(player.getUniqueId());
        PlayerTreeConfig.ChoppingMethod method = config.getChoppingMethod();

        // Ensure player is looking towards the tree
        faceLocation(baseBlock.getLocation());

        // Track chopped tree coordinate to prevent infinite attempts
        String baseKey = baseBlock.getX() + "," + baseBlock.getY() + "," + baseBlock.getZ();
        choppedTrees.add(baseKey);

        List<Block> logsToBreak = new ArrayList<>();

        if (method == PlayerTreeConfig.ChoppingMethod.REACHABLE) {
            // REACHABLE: Chop upwards from base up to eye height + 3 blocks (e.g. 5 blocks)
            Block currentBlock = baseBlock;
            for (int h = 0; h < 5; h++) {
                if (isLogBlock(currentBlock.getType()) || isMangroveRootBlock(currentBlock.getType())) {
                    logsToBreak.add(currentBlock);
                    currentBlock = currentBlock.getRelative(BlockFace.UP);
                } else {
                    break;
                }
            }
        } else {
            // WHOLE TREE: Recursive BFS with radial adjacency of 2
            Queue<Block> queue = new LinkedList<>();
            Set<Block> visited = new HashSet<>();

            queue.add(baseBlock);
            visited.add(baseBlock);

            int maxBlocks = 512; // safety cap
            while (!queue.isEmpty() && logsToBreak.size() < maxBlocks) {
                Block curr = queue.poll();
                logsToBreak.add(curr);

                // Radial adjacency of 2 (dx, dy, dz in [-2, 2])
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            Block neighbor = curr.getRelative(dx, dy, dz);
                            if (visited.contains(neighbor)) continue;

                            if (isLogBlock(neighbor.getType()) || isMangroveRootBlock(neighbor.getType())) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        // Remove Beehives and kill bees if enabled globally within a 5-block radius of the player
        if (config.isRemoveBeesEnabled()) {
            Location pLoc = player.getLocation();
            World world = pLoc.getWorld();
            if (world != null) {
                int px = pLoc.getBlockX();
                int py = pLoc.getBlockY();
                int pz = pLoc.getBlockZ();

                // Scan 5-block radius horizontally and vertically around player
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            Block b = world.getBlockAt(px + dx, py + dy, pz + dz);
                            Material type = b.getType();
                            if (type == Material.BEE_NEST || type == Material.BEEHIVE) {
                                org.bukkit.block.BlockState state = b.getState();
                                if (state instanceof org.bukkit.block.Beehive beehive) {
                                    List<org.bukkit.entity.Bee> bees = beehive.releaseEntities();
                                    for (org.bukkit.entity.Bee bee : bees) {
                                        bee.setHealth(0.0);
                                    }
                                }
                                b.breakNaturally();
                            }
                        }
                    }
                }
            }
            // Exterminate all nearby flying bees within 12 blocks of player
            killNearbyBees(pLoc, 12.0);
        }

        // Break wood blocks
        int brokenCount = 0;
        for (Block log : logsToBreak) {
            if (!verifyToolAndDurability()) {
                break; // swap tool failed or low durability paused
            }

            Material brokenType = log.getType();
            ItemStack axe = player.getInventory().getItemInMainHand();

            log.breakNaturally(axe);
            
            if (log.getType() == brokenType) {
                plugin.getLogger().warning("[HereLoggy] Failed to chop wood block " + brokenType + " at " + log.getLocation() + " for player " + player.getName() + " (is area protected?). Skipping this tree.");
                // Add the base block coordinate to permanently protected trees list
                protectedTrees.add(baseKey);
                break;
            }

            brokenCount++;
            applyDurabilityDamage(axe, 1);

            // Award AuraSkills Foraging XP according to the type of wood broken
            double xpForLog = auraSkillsHelper != null ? auraSkillsHelper.getBaseXpForLog(brokenType) : 1.0;
            if (auraSkillsHelper != null) {
                auraSkillsHelper.addForagingXp(player, xpForLog);
            }
            
            // Award HereRolePlay Collect XP (aligned to native wood break XP values)
            if (hereRolePlayHelper.isAvailable()) {
                double hrpXp = 1.0;
                String typeName = brokenType.name();
                if (typeName.contains("PALE_OAK")) {
                    hrpXp = 2.0;
                } else if (typeName.contains("CRIMSON") || typeName.contains("WARPED")) {
                    hrpXp = 1.5;
                }
                hereRolePlayHelper.addCollectXp(player, hrpXp);
            }

            collectedLogs.put(brokenType, collectedLogs.getOrDefault(brokenType, 0) + 1);

            // Foliage destruction & collection: scan 3-block radius around chopped log block
            if (method == PlayerTreeConfig.ChoppingMethod.WHOLE_TREE) {
                destroyFoliageAround(log.getLocation());
                collectDropsAt(log.getLocation(), logType);
            }
        }

        // Attract drops and apply XP
        attractDropsAndRepairMending(baseBlock.getLocation(), logType);

        // Optional Sapling Replanting
        if (settings.isReplantEnabled()) {
            for (Block log : logsToBreak) {
                attemptSaplingReplant(log, logType);
            }
        }

        // Tree chopping finished successfully
    }

    private void destroyFoliageAround(Location loc) {
        World world = loc.getWorld();
        int lx = loc.getBlockX();
        int ly = loc.getBlockY();
        int lz = loc.getBlockZ();

        ItemStack axe = player.getInventory().getItemInMainHand();

        // 3-block radius
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block block = world.getBlockAt(lx + dx, ly + dy, lz + dz);
                    Material type = block.getType();

                    if (isLeavesBlock(type) || isNetherFoliageBlock(type)) {
                        block.breakNaturally(axe);
                        collectedFoliage.put(type, collectedFoliage.getOrDefault(type, 0) + 1);
                    }
                }
            }
        }
    }

    private void attemptSaplingReplant(Block baseBlock, Material logType) {
        Block ground = baseBlock.getRelative(BlockFace.DOWN);
        if (!isSoilBlock(ground.getType())) return;

        Material saplingType = TreeConfigUI.getSaplingMaterial(logType);
        if (hasItem(saplingType)) {
            removeInventoryItem(saplingType);
            baseBlock.setType(saplingType);
            replantedCount++;
            baseBlock.getWorld().playSound(baseBlock.getLocation(), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.0f);
        }
    }

    private boolean isSoilBlock(Material mat) {
        return mat == Material.GRASS_BLOCK || mat == Material.DIRT || mat == Material.COARSE_DIRT ||
               mat == Material.PODZOL || mat == Material.ROOTED_DIRT || mat == Material.MUD ||
               mat == Material.CLAY || mat == Material.MOSS_BLOCK || mat == Material.MYCELIUM ||
               mat == Material.CRIMSON_NYLIUM || mat == Material.WARPED_NYLIUM ||
               mat == Material.SOUL_SAND || mat == Material.SOUL_SOIL;
    }

    private boolean isLogBlock(Material mat) {
        String name = mat.name();
        return Tag.LOGS.isTagged(mat) || name.contains("_LOG") || name.contains("_WOOD") || name.contains("_STEM") || name.contains("_HYPHAE");
    }

    private void killNearbyBees(Location loc, double radius) {
        World world = loc.getWorld();
        if (world == null) return;
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof org.bukkit.entity.Bee)
                .forEach(entity -> {
                    if (entity instanceof org.bukkit.entity.Bee bee) {
                        bee.setHealth(0.0);
                    }
                });
    }

    private boolean isLeavesBlock(Material mat) {
        String name = mat.name();
        return Tag.LEAVES.isTagged(mat) || name.contains("_LEAVES");
    }

    private boolean isNetherFoliageBlock(Material mat) {
        return mat == Material.NETHER_WART_BLOCK || mat == Material.WARPED_WART_BLOCK || mat == Material.SHROOMLIGHT;
    }

    private boolean isMangroveRootBlock(Material mat) {
        return mat == Material.MANGROVE_ROOTS || mat == Material.MUDDY_MANGROVE_ROOTS;
    }

    private void faceLocation(Location loc) {
        Location playerEye = player.getEyeLocation();
        Vector dir = loc.toVector().subtract(playerEye.toVector());
        if (dir.lengthSquared() > 0.01) {
            dir.normalize();
            Location look = player.getLocation();
            look.setDirection(dir);
            player.teleport(look);
        }
    }

    private boolean verifyToolAndDurability() {
        int hotbarSlot = findToolSlotInHotbar("AXE");
        if (hotbarSlot != -1) {
            if (player.getInventory().getHeldItemSlot() != hotbarSlot) {
                player.getInventory().setHeldItemSlot(hotbarSlot);
            }
        } else {
            // Check main inventory and swap
            int invSlot = findToolSlotInInventory("AXE");
            if (invSlot != -1) {
                ItemStack tool = player.getInventory().getItem(invSlot);
                ItemStack currentSlot0 = player.getInventory().getItem(0);

                player.getInventory().setItem(invSlot, currentSlot0);
                player.getInventory().setItem(0, tool);
                player.getInventory().setHeldItemSlot(0);
                player.updateInventory();
            } else {
                if (refuelFromToolSupplyChest()) {
                    // Refuelled successfully!
                } else {
                    player.sendMessage(Component.text("No suitable tool (Axe) found! Auto-chopping paused.")
                            .color(NamedTextColor.RED));
                    cancel();
                    return false;
                }
            }
        }

        // Durability check
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getItemMeta() instanceof Damageable damageable) {
            int maxDur = held.getType().getMaxDurability();
            int currentDur = maxDur - damageable.getDamage();
            SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
            int threshold = setup != null ? setup.getDurabilityThreshold() : 10;

            if (currentDur <= threshold) {
                if (refuelFromToolSupplyChest()) {
                    player.sendMessage(Component.text("Axe durability depleted! Swapped axe at Supply Chest.").color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Axe durability low (" + currentDur + ")! Paused to prevent breakage.")
                            .color(NamedTextColor.RED));
                    cancel();
                    return false;
                }
            }
        }

        return true;
    }

    private int findToolSlotInHotbar(String keyword) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains(keyword) && !item.getType().name().contains("PICKAXE") && item.getAmount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private int findToolSlotInInventory(String keyword) {
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType().name().contains(keyword) && !item.getType().name().contains("PICKAXE") && item.getAmount() > 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean refuelFromToolSupplyChest() {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup == null || setup.getToolSupplyChest() == null) return false;

        Location chestLoc = setup.getToolSupplyChest();
        if (!(chestLoc.getBlock().getState() instanceof Container container)) return false;

        Inventory chestInv = container.getInventory();
        int freshSlot = -1;
        for (int i = 0; i < chestInv.getSize(); i++) {
            ItemStack item = chestInv.getItem(i);
            if (item != null && item.getType().name().contains("AXE") && !item.getType().name().contains("PICKAXE")) {
                if (item.getItemMeta() instanceof Damageable dmg) {
                    if (item.getType().getMaxDurability() - dmg.getDamage() > 20) {
                        freshSlot = i;
                        break;
                    }
                } else {
                    freshSlot = i;
                    break;
                }
            }
        }

        if (freshSlot == -1) return false;

        Location originalLoc = player.getLocation();
        player.teleport(chestLoc.clone().add(0.5, 1.0, 0.5));

        ItemStack freshTool = chestInv.getItem(freshSlot);
        ItemStack wornTool = player.getInventory().getItemInMainHand();

        chestInv.setItem(freshSlot, wornTool);
        player.getInventory().setItemInMainHand(freshTool);
        player.updateInventory();

        refuelsCount++;
        player.teleport(originalLoc);
        return true;
    }

    private void attractDropsAndRepairMending(Location loc, Material logType) {
        double radius = 6.0;
        World world = loc.getWorld();

        // 1. Attract Dropped Items
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof Item)
                .forEach(entity -> {
                    Item dropped = (Item) entity;
                    ItemStack stack = dropped.getItemStack();
                    routeItemToStorage(stack, logType);
                    dropped.remove();
                });

        // 2. XP attraction and Mending
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof ExperienceOrb)
                .forEach(entity -> {
                    ExperienceOrb orb = (ExperienceOrb) entity;
                    int xp = orb.getExperience();

                    int leftoverXp = applySharedMendingRepair(xp);
                    if (leftoverXp > 0) {
                        player.giveExp(leftoverXp);
                    }

                    if (auraSkillsHelper != null && auraSkillsHelper.isAvailable()) {
                        auraSkillsHelper.addForagingXp(player, xp * 2.0);
                    }


                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
                    orb.remove();
                });

        // Base Foraging XP reward
        if (auraSkillsHelper != null) {
            auraSkillsHelper.addForagingXp(player, 15.0);
        }
    }

    private int applySharedMendingRepair(int xp) {
        if (xp <= 0) return 0;

        int remainingXp = xp;

        while (remainingXp > 0) {
            List<ItemStack> eligible = new ArrayList<>();
            
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (isMendableAndDamaged(mainHand)) eligible.add(mainHand);
            
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (isMendableAndDamaged(offHand)) eligible.add(offHand);
            
            ItemStack helmet = player.getInventory().getHelmet();
            if (isMendableAndDamaged(helmet)) eligible.add(helmet);
            
            ItemStack chest = player.getInventory().getChestplate();
            if (isMendableAndDamaged(chest)) eligible.add(chest);
            
            ItemStack leggings = player.getInventory().getLeggings();
            if (isMendableAndDamaged(leggings)) eligible.add(leggings);
            
            ItemStack boots = player.getInventory().getBoots();
            if (isMendableAndDamaged(boots)) eligible.add(boots);

            if (eligible.isEmpty()) {
                break;
            }

            // Pick one randomly
            ItemStack toRepair = eligible.get(new Random().nextInt(eligible.size()));
            if (toRepair.getItemMeta() instanceof Damageable dmg) {
                int damage = dmg.getDamage();
                int xpToUse = Math.min(remainingXp, (int) Math.ceil(damage / 2.0));
                int repairAmount = Math.min(damage, xpToUse * 2);
                
                dmg.setDamage(damage - repairAmount);
                toRepair.setItemMeta(dmg);
                player.updateInventory();
                
                remainingXp -= xpToUse;
            } else {
                break; // Safety fallback
            }
        }

        return remainingXp;
    }

    private boolean isMendableAndDamaged(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return false;
        if (item.getEnchantmentLevel(Enchantment.MENDING) <= 0) return false;
        if (item.getItemMeta() instanceof Damageable dmg) {
            return dmg.getDamage() > 0;
        }
        return false;
    }

    private int getSaplingCount(Material mat) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void routeItemToStorage(ItemStack stack, Material logType) {
        Material mat = stack.getType();
        if (isSapling(mat)) {
            int currentCount = getSaplingCount(mat);
            int maxAllowed = 64; // 1 stack limit

            if (currentCount >= maxAllowed) {
                // Already have a full stack, so this entire stack is treasure!
                depositIntoChest(stack, false);
            } else {
                int needed = maxAllowed - currentCount;
                if (stack.getAmount() <= needed) {
                    // We can take the whole stack into inventory
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        ItemStack fall = leftover.values().iterator().next();
                        player.getWorld().dropItemNaturally(player.getLocation(), fall);
                    }
                } else {
                    // Split the stack: keep enough to reach 64, deposit the rest
                    ItemStack toKeep = stack.clone();
                    toKeep.setAmount(needed);

                    ItemStack toDeposit = stack.clone();
                    toDeposit.setAmount(stack.getAmount() - needed);

                    // Add toKeep to inventory
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toKeep);
                    if (!leftover.isEmpty()) {
                        ItemStack fall = leftover.values().iterator().next();
                        player.getWorld().dropItemNaturally(player.getLocation(), fall);
                    }

                    // Deposit toDeposit to chest
                    depositIntoChest(toDeposit, false);
                }
            }
            return;
        }

        if (isLogBlock(mat)) {
            TreeSettings settings = configManager.getTreeSettings(player.getUniqueId(), logType);
            if (settings.isJunkEnabled()) {
                depositIntoChest(stack, false);
            } else {
                depositIntoChest(stack, true);
            }
        } else {
            // Non-log item (e.g. apple, stick) is always considered JUNK (non-logs) and routed to trash chest
            depositIntoChest(stack, false);
        }
    }

    private void depositIntoChest(ItemStack stack, boolean isKeepChest) {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup != null) {
            Location chestLoc = isKeepChest ? setup.getKeepChest() : setup.getTrashChest();
            if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                Inventory chestInv = container.getInventory();
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(stack);
                if (leftover.isEmpty()) {
                    return;
                } else {
                    stack.setAmount(leftover.values().iterator().next().getAmount());
                }
            }
        }

        // Fallback: place in player inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            ItemStack fall = leftover.values().iterator().next();
            player.getWorld().dropItemNaturally(player.getLocation(), fall);
        }
    }

    private boolean isInventoryFull() {
        return player.getInventory().firstEmpty() == -1;
    }

    private boolean attemptDumpToChests() {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup == null || setup.getKeepChest() == null || setup.getTrashChest() == null) return false;

        Inventory playerInv = player.getInventory();
        boolean successfullyClearedAny = false;

        // Find the single largest stack of food (edible material) in the inventory to keep
        int bestFoodSlot = -1;
        int maxFoodAmount = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item != null && item.getAmount() > 0 && item.getType().isEdible()) {
                if (item.getAmount() > maxFoodAmount) {
                    maxFoodAmount = item.getAmount();
                    bestFoodSlot = i;
                }
            }
        }

        // Track how many we have chosen to keep so far for each sapling type
        Map<Material, Integer> keptSaplingCounts = new HashMap<>();

        for (int i = 0; i < 36; i++) {
            if (i == bestFoodSlot) continue; // Keep the biggest stack of food!

            ItemStack item = playerInv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            Material mat = item.getType();
            if (isProtectedItem(mat)) continue; // Keep vital survival gear/tools

            Location chestLoc = null;

            if (isSapling(mat)) {
                int kept = keptSaplingCounts.getOrDefault(mat, 0);
                if (kept >= 64) {
                    // Already have a full stack kept, dump this stack!
                    chestLoc = setup.getTrashChest();
                } else {
                    int canKeep = 64 - kept;
                    if (item.getAmount() <= canKeep) {
                        keptSaplingCounts.put(mat, kept + item.getAmount());
                        continue; // Keep the entire stack in inventory
                    } else {
                        // Split it: keep canKeep in inventory, dump the rest
                        ItemStack toKeep = item.clone();
                        toKeep.setAmount(canKeep);

                        ItemStack toDump = item.clone();
                        toDump.setAmount(item.getAmount() - canKeep);

                        chestLoc = setup.getTrashChest();
                        if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                            Inventory chestInv = container.getInventory();
                            HashMap<Integer, ItemStack> leftover = chestInv.addItem(toDump);
                            if (leftover.isEmpty()) {
                                playerInv.setItem(i, toKeep);
                                successfullyClearedAny = true;
                                keptSaplingCounts.put(mat, 64);
                            } else {
                                int remainingAmount = leftover.values().iterator().next().getAmount();
                                ItemStack newStack = item.clone();
                                newStack.setAmount(canKeep + remainingAmount);
                                playerInv.setItem(i, newStack);
                                if (newStack.getAmount() < item.getAmount()) {
                                    successfullyClearedAny = true;
                                }
                                keptSaplingCounts.put(mat, kept + (item.getAmount() - remainingAmount));
                            }
                        }
                        continue;
                    }
                }
            } else if (isLogBlock(mat)) {
                Material baseLog = PlayerTreeConfig.normalizeLogType(mat);
                TreeSettings settings = configManager.getTreeSettings(player.getUniqueId(), baseLog);
                boolean isKeep = !settings.isJunkEnabled();
                chestLoc = isKeep ? setup.getKeepChest() : setup.getTrashChest();
            } else {
                // Non-log item (e.g. apple, stick) is always JUNK
                chestLoc = setup.getTrashChest();
            }

            if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                Inventory chestInv = container.getInventory();
                HashMap<Integer, ItemStack> leftover = chestInv.addItem(item);
                if (leftover.isEmpty()) {
                    playerInv.setItem(i, null);
                    successfullyClearedAny = true;
                } else {
                    ItemStack remaining = leftover.values().iterator().next();
                    playerInv.setItem(i, remaining);
                    if (remaining.getAmount() < item.getAmount()) {
                        successfullyClearedAny = true;
                    }
                }
            }
        }

        if (successfullyClearedAny) {
            player.updateInventory();
        }
        return successfullyClearedAny;
    }

    public void sendActivitySummary() {
        if (collectedLogs.isEmpty() && collectedFoliage.isEmpty() && refuelsCount == 0 && replantedCount == 0) {
            return;
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("       HereLoggy Activity Summary       ").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));

        if (!collectedLogs.isEmpty()) {
            player.sendMessage(Component.text("★ Wood Logs Collected:").color(NamedTextColor.YELLOW));
            for (Map.Entry<Material, Integer> entry : collectedLogs.entrySet()) {
                player.sendMessage(Component.text("  - " + formatMaterialName(entry.getKey()) + ": x" + entry.getValue())
                        .color(NamedTextColor.GREEN));
            }
        }

        if (!collectedFoliage.isEmpty()) {
            player.sendMessage(Component.text("■ Foliage & Items Cleared:").color(NamedTextColor.DARK_GRAY));
            int totalLeaves = 0;
            for (int count : collectedFoliage.values()) {
                totalLeaves += count;
            }
            player.sendMessage(Component.text("  - Total leaves & nether foliage: x" + totalLeaves)
                    .color(NamedTextColor.GRAY));
        }

        if (replantedCount > 0) {
            player.sendMessage(Component.text("🌱 Saplings Replanted: " + replantedCount).color(NamedTextColor.AQUA));
        }

        if (refuelsCount > 0) {
            player.sendMessage(Component.text("🔧 Axes Replaced: " + refuelsCount).color(NamedTextColor.LIGHT_PURPLE));
        }

        player.sendMessage(Component.text("-----------------------------------").color(NamedTextColor.GRAY));
    }

    private String formatMaterialName(Material mat) {
        String[] split = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String s : split) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return sb.toString();
    }

    private boolean hasItem(Material material) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material && offHand.getAmount() > 0) {
            return true;
        }
        return player.getInventory().contains(material);
    }

    private void removeInventoryItem(Material material) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material && offHand.getAmount() > 0) {
            offHand.setAmount(offHand.getAmount() - 1);
            player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
            player.updateInventory();
            return;
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == material) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(i, item.getAmount() > 0 ? item : null);
                player.updateInventory();
                return;
            }
        }
    }

    private int findFoodSlotInInventory() {
        // First prioritize hotbar for consistency
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            Material mat = item.getType();
            if (mat.isEdible()) {
                return i;
            }
        }
        // Then search the rest of the inventory
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            Material mat = item.getType();
            if (mat.isEdible()) {
                return i;
            }
        }
        return -1;
    }

    private void handleFeeding() {
        int foodSlot = findFoodSlotInInventory();
        if (foodSlot == -1) return;

        ItemStack foodStack = player.getInventory().getItem(foodSlot);
        Material foodType = foodStack.getType();

        int hungerRestore = 4;
        float saturationRestore = 6.0f;

        switch (foodType) {
            case COOKED_BEEF, COOKED_PORKCHOP, PUMPKIN_PIE -> {
                hungerRestore = 8;
                saturationRestore = 12.8f;
            }
            case GOLDEN_CARROT -> {
                hungerRestore = 6;
                saturationRestore = 14.4f;
            }
            case COOKED_MUTTON, COOKED_SALMON -> {
                hungerRestore = 6;
                saturationRestore = 9.6f;
            }
            case COOKED_CHICKEN -> {
                hungerRestore = 6;
                saturationRestore = 7.2f;
            }
            case COOKED_COD, BAKED_POTATO, BREAD -> {
                hungerRestore = 5;
                saturationRestore = 6.0f;
            }
            case GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE -> {
                hungerRestore = 4;
                saturationRestore = 9.6f;
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 100, 1));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 2400, 0));
            }
            case APPLE, CARROT -> {
                hungerRestore = 4;
                saturationRestore = 2.4f;
            }
            case COOKED_RABBIT -> {
                hungerRestore = 5;
                saturationRestore = 6.0f;
            }
            case MELON_SLICE, COOKIE, SWEET_BERRIES, GLOW_BERRIES -> {
                hungerRestore = 2;
                saturationRestore = 1.2f;
            }
            default -> {}
        }

        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            foodStack.setAmount(foodStack.getAmount() - 1);
            player.getInventory().setItem(foodSlot, foodStack.getAmount() > 0 ? foodStack : null);
            player.updateInventory();
        }

        int newFood = Math.min(20, player.getFoodLevel() + hungerRestore);
        float newSat = Math.min(20.0f, player.getSaturation() + saturationRestore);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
        player.sendActionBar(Component.text("😋 Consumed " + formatMaterialName(foodType) + " to keep well-fed!").color(NamedTextColor.GREEN));

        chopPause = 15;
    }

    private boolean handleDefense() {
        double attackRadius = 3.5;
        Location loc = player.getLocation();
        org.bukkit.entity.LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, attackRadius, attackRadius, attackRadius)) {
            if (entity instanceof org.bukkit.entity.Monster ||
                entity instanceof org.bukkit.entity.Slime ||
                entity instanceof org.bukkit.entity.Phantom ||
                entity instanceof org.bukkit.entity.Spider) {

                if (entity instanceof org.bukkit.entity.LivingEntity living) {
                    if (!living.isDead() && player.hasLineOfSight(living)) {
                        double distSq = loc.distanceSquared(living.getLocation());
                        if (distSq < closestDistSq) {
                            closestDistSq = distSq;
                            closest = living;
                        }
                    }
                }
            }
        }

        if (closest == null) return false;

        // Equip best weapon
        int slot = findBestWeaponSlot();
        if (slot != -1) {
            if (player.getInventory().getHeldItemSlot() != slot) {
                player.getInventory().setHeldItemSlot(slot);
            }
        }

        // Face monster and sweep
        Location targetEye = closest.getEyeLocation();
        Location playerEye = player.getEyeLocation();
        Vector dir = targetEye.toVector().subtract(playerEye.toVector()).normalize();

        Location look = player.getLocation();
        look.setDirection(dir);
        player.teleport(look);

        player.attack(closest);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        player.sendActionBar(Component.text("⚔ Fending off " + closest.getName() + "!").color(NamedTextColor.RED));

        return true;
    }

    private int findBestWeaponSlot() {
        int bestSlot = -1;
        double maxDamage = -1.0;

        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getAmount() == 0) continue;

            String name = item.getType().name();
            double dmgValue = 0.0;

            if (name.contains("SWORD")) {
                dmgValue = 100.0;
            } else if (name.contains("AXE") && !name.contains("PICKAXE")) {
                dmgValue = 80.0;
            } else if (name.contains("PICKAXE")) {
                dmgValue = 60.0;
            }

            if (name.startsWith("NETHERITE")) {
                dmgValue += 5.0;
            } else if (name.startsWith("DIAMOND")) {
                dmgValue += 4.0;
            } else if (name.startsWith("IRON")) {
                dmgValue += 3.0;
            } else if (name.startsWith("STONE")) {
                dmgValue += 2.0;
            }

            if (dmgValue > maxDamage) {
                maxDamage = dmgValue;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean isSapling(Material material) {
        return material == Material.OAK_SAPLING ||
               material == Material.SPRUCE_SAPLING ||
               material == Material.BIRCH_SAPLING ||
               material == Material.JUNGLE_SAPLING ||
               material == Material.ACACIA_SAPLING ||
               material == Material.DARK_OAK_SAPLING ||
               material == Material.MANGROVE_PROPAGULE ||
               material == Material.CHERRY_SAPLING ||
               material == Material.CRIMSON_FUNGUS ||
               material == Material.WARPED_FUNGUS;
    }

    private Material getLogTypeFromSapling(Material saplingType) {
        return switch (saplingType) {
            case OAK_SAPLING -> Material.OAK_LOG;
            case SPRUCE_SAPLING -> Material.SPRUCE_LOG;
            case BIRCH_SAPLING -> Material.BIRCH_LOG;
            case JUNGLE_SAPLING -> Material.JUNGLE_LOG;
            case ACACIA_SAPLING -> Material.ACACIA_LOG;
            case DARK_OAK_SAPLING -> Material.DARK_OAK_LOG;
            case MANGROVE_PROPAGULE -> Material.MANGROVE_LOG;
            case CHERRY_SAPLING -> Material.CHERRY_LOG;
            case CRIMSON_FUNGUS -> Material.CRIMSON_STEM;
            case WARPED_FUNGUS -> Material.WARPED_STEM;
            default -> Material.OAK_LOG;
        };
    }

    private void collectDropsAt(Location loc, Material logType) {
        double radius = 4.5;
        World world = loc.getWorld();
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof Item)
                .forEach(entity -> {
                    Item dropped = (Item) entity;
                    ItemStack stack = dropped.getItemStack();
                    routeItemToStorage(stack, logType);
                    dropped.remove();
                });
    }

    private void applyDurabilityDamage(ItemStack tool, int amount) {
        if (tool == null || tool.getType().isAir()) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        if (tool.getItemMeta() instanceof Damageable dmg) {
            int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
            int damageToApply = 0;

            for (int i = 0; i < amount; i++) {
                if (unbreakingLevel > 0) {
                    if (Math.random() < (1.0 / (unbreakingLevel + 1.0))) {
                        damageToApply++;
                    }
                } else {
                    damageToApply++;
                }
            }

            if (damageToApply > 0) {
                dmg.setDamage(dmg.getDamage() + damageToApply);
                tool.setItemMeta(dmg);
                player.updateInventory();
            }
        }
    }

    private boolean isFoliageOrLog(Material mat) {
        return isLogBlock(mat) || isLeavesBlock(mat) || isNetherFoliageBlock(mat) || isMangroveRootBlock(mat);
    }

    private boolean isSafeStandLocation(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);
        
        Material feetType = feet.getType();
        Material headType = head.getType();
        Material groundType = ground.getType();

        boolean feetPassable = !feetType.isSolid() || isFoliageOrLog(feetType);
        boolean headPassable = !headType.isSolid() || isFoliageOrLog(headType);
        boolean groundSolid = groundType.isSolid() && !isFoliageOrLog(groundType);

        return feetPassable && headPassable && groundSolid;
    }

    private void dumpSaplingsToChests() {
        SetupConfiguration setup = setupManager.getSetupConfig(player.getUniqueId());
        if (setup == null || (setup.getKeepChest() == null && setup.getTrashChest() == null)) return;

        Inventory playerInv = player.getInventory();
        boolean successfullyClearedAny = false;

        // Track how many we have chosen to keep so far for each sapling type
        Map<Material, Integer> keptSaplingCounts = new HashMap<>();

        for (int i = 0; i < 36; i++) {
            ItemStack item = playerInv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            Material mat = item.getType();
            if (isSapling(mat)) {
                int kept = keptSaplingCounts.getOrDefault(mat, 0);
                if (kept >= 64) {
                    // We already kept a full stack of this sapling type, so dump this one!
                    Location chestLoc = setup.getTrashChest();
                    if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                        Inventory chestInv = container.getInventory();
                        HashMap<Integer, ItemStack> leftover = chestInv.addItem(item);
                        if (leftover.isEmpty()) {
                            playerInv.setItem(i, null);
                            successfullyClearedAny = true;
                        } else {
                            ItemStack remaining = leftover.values().iterator().next();
                            playerInv.setItem(i, remaining);
                            if (remaining.getAmount() < item.getAmount()) {
                                successfullyClearedAny = true;
                            }
                        }
                    }
                } else {
                    // We can keep some or all of this stack
                    int canKeep = 64 - kept;
                    if (item.getAmount() <= canKeep) {
                        // Keep the entire item stack
                        keptSaplingCounts.put(mat, kept + item.getAmount());
                    } else {
                        // Keep a portion, dump the rest
                        ItemStack toKeep = item.clone();
                        toKeep.setAmount(canKeep);

                        ItemStack toDump = item.clone();
                        toDump.setAmount(item.getAmount() - canKeep);

                        Location chestLoc = setup.getTrashChest();
                        if (chestLoc != null && chestLoc.getBlock().getState() instanceof Container container) {
                            Inventory chestInv = container.getInventory();
                            HashMap<Integer, ItemStack> leftover = chestInv.addItem(toDump);
                            if (leftover.isEmpty()) {
                                playerInv.setItem(i, toKeep);
                                successfullyClearedAny = true;
                                keptSaplingCounts.put(mat, 64);
                            } else {
                                int remainingAmount = leftover.values().iterator().next().getAmount();
                                ItemStack newStack = item.clone();
                                newStack.setAmount(canKeep + remainingAmount);
                                playerInv.setItem(i, newStack);
                                if (newStack.getAmount() < item.getAmount()) {
                                    successfullyClearedAny = true;
                                }
                                keptSaplingCounts.put(mat, kept + (item.getAmount() - remainingAmount));
                            }
                        }
                    }
                }
            }
        }

        if (successfullyClearedAny) {
            player.updateInventory();
            player.sendMessage(Component.text("Excess saplings cleared into deposit chests.").color(NamedTextColor.GREEN));
        }
    }

    private void triggerRescan() {
        if (scanManager == null || !plugin.getSelectionManager().hasCompleteSelection(player.getUniqueId())) {
            return;
        }
        Location pointA = plugin.getSelectionManager().getPointA(player.getUniqueId());
        Location pointB = plugin.getSelectionManager().getPointB(player.getUniqueId());
        
        // Preserve active target to restore progress post-rescan
        Location currentTarget = (currentIndex >= 0 && currentIndex < path.size()) ? path.get(currentIndex).clone() : null;

        scanManager.scanAreaAsync(player.getUniqueId(), pointA, pointB, result -> {
            this.scanResult = result;
            List<Location> newPath = PathGenerator.generateSafePath(result);
            if (!newPath.isEmpty()) {
                path.clear();
                path.addAll(newPath);
                
                if (currentTarget != null) {
                    currentIndex = PathGenerator.findClosestIndex(newPath, currentTarget);
                } else {
                    currentIndex = PathGenerator.findClosestIndex(newPath, player.getLocation());
                }
            }
            choppedTrees.clear();
        });
    }

    private boolean isClearableFoliage(Material mat) {
        String name = mat.name();
        return isLeavesBlock(mat) || isNetherFoliageBlock(mat) || mat == Material.VINE ||
               mat == Material.SHORT_GRASS || mat == Material.TALL_GRASS ||
               mat == Material.FERN || mat == Material.LARGE_FERN ||
               name.contains("LEAVES") || name.contains("LICHEN") || name.contains("MOSS");
    }

    private void scanAndClearFoliage(Location current) {
        World world = current.getWorld();
        int px = current.getBlockX();
        int py = current.getBlockY();
        int pz = current.getBlockZ();

        int foliageCount = 0;
        int logCount = 0;
        
        List<Block> freeDirtBlocks = new ArrayList<>();

        // Scan 5-block area around player to count logs and foliage
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Block block = world.getBlockAt(px + dx, py + dy, pz + dz);
                    Material type = block.getType();
                    if (isClearableFoliage(type)) {
                        foliageCount++;
                    } else if (isLogBlock(type)) {
                        logCount++;
                    } else if (isSoilBlock(type)) {
                        if (block.getRelative(BlockFace.UP).getType().isAir()) {
                            freeDirtBlocks.add(block);
                        }
                    }
                }
            }
        }

        // If there is too much foliage (e.g. >= 10 blocks), clear foliage blocks in 3-block radius
        if (foliageCount >= 10) {
            boolean clearedAny = false;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        Block block = world.getBlockAt(px + dx, py + dy, pz + dz);
                        Material type = block.getType();
                        if (isClearableFoliage(type)) {
                            // Ensure player has a tool and enough durability
                            if (!verifyToolAndDurability()) {
                                return; // Stop clearing if no tool or low durability
                            }

                            ItemStack axe = player.getInventory().getItemInMainHand();
                            
                            // Break block naturally at cost of axe durability
                            block.breakNaturally(axe);
                            applyDurabilityDamage(axe, 1);
                            
                            collectedFoliage.put(type, collectedFoliage.getOrDefault(type, 0) + 1);
                            clearedAny = true;
                        }
                    }
                }
            }
            if (clearedAny) {
                player.sendActionBar(Component.text("🌿 Clearing thick foliage to pave path...").color(NamedTextColor.YELLOW));
                // Attract drops from cleared foliage
                collectFoliageDropsAt(current);
            }
        }
        
        if (!freeDirtBlocks.isEmpty()) {
            proactivelyPlantSaplings(freeDirtBlocks);
        }
    }

    private void proactivelyPlantSaplings(List<Block> freeDirtBlocks) {
        Material saplingToPlant = null;
        PlayerTreeConfig config = configManager.getPlayerConfig(player.getUniqueId());
        
        for (Map.Entry<Material, TreeSettings> entry : config.getAllTreeSettings().entrySet()) {
            if (entry.getValue().isProactivePlantEnabled()) {
                Material sapMat = TreeConfigUI.getSaplingMaterial(entry.getKey());
                if (hasItem(sapMat)) {
                    saplingToPlant = sapMat;
                    break;
                }
            }
        }
        
        if (saplingToPlant == null) return;
        
        int planted = 0;
        Collections.shuffle(freeDirtBlocks);
        for (Block dirt : freeDirtBlocks) {
            if (planted >= 2) break; // Limit 2 saplings per scan tick to spread it out
            Block space = dirt.getRelative(BlockFace.UP);
            if (space.getType().isAir() && hasItem(saplingToPlant)) {
                removeInventoryItem(saplingToPlant);
                space.setType(saplingToPlant);
                space.getWorld().playSound(space.getLocation(), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.0f);
                replantedCount++;
                planted++;
            }
        }
    }

    private void collectFoliageDropsAt(Location loc) {
        double radius = 4.5;
        World world = loc.getWorld();
        world.getNearbyEntities(loc, radius, radius, radius, entity -> entity instanceof Item)
                .forEach(entity -> {
                    Item dropped = (Item) entity;
                    ItemStack stack = dropped.getItemStack();
                    // Route non-log items (saplings, apples, sticks) to trash/junk storage
                    routeItemToStorage(stack, Material.OAK_LOG);
                    dropped.remove();
                });
    }

    public void handleSuffocationRescue() {
        Location current = player.getLocation();
        World world = current.getWorld();
        if (world == null) return;

        player.sendMessage(Component.text("⚠️ You are suffocating! Activating emergency rescue...").color(NamedTextColor.RED));

        int px = current.getBlockX();
        int py = current.getBlockY();
        int pz = current.getBlockZ();

        // 1. Eliminate any foliage or logs in a 5x5x5 area around the player
        int radius = 2; // dx/dz in [-2, 2], dy in [-1, 3]
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(px + dx, py + dy, pz + dz);
                    Material type = block.getType();
                    if (isFoliageOrLog(type) || isClearableFoliage(type)) {
                        block.breakNaturally();
                    }
                }
            }
        }

        // 2. Find the next available safe spot and teleport
        // Candidate 1: Try path coordinates starting from the current index
        if (!path.isEmpty()) {
            for (int i = 0; i < path.size(); i++) {
                int index = (currentIndex + i) % path.size();
                Location node = path.get(index);
                if (isSafeStandLocation(world, node.getBlockX(), node.getBlockY(), node.getBlockZ())) {
                    Location safeLoc = node.clone().add(0.5, 0.0, 0.5);
                    safeLoc.setPitch(current.getPitch());
                    safeLoc.setYaw(current.getYaw());
                    player.teleport(safeLoc);
                    currentIndex = index; // Move index to the safe spot we teleported to!
                    player.sendMessage(Component.text("✔ Teleported to a safe spot along the path!").color(NamedTextColor.GREEN));
                    return;
                }
            }
        }

        // Candidate 2: Spiral search around current location
        Location safeSpot = null;
        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;

                    for (int dy = -3; dy <= 3; dy++) {
                        int tx = px + dx;
                        int ty = py + dy;
                        int tz = pz + dz;

                        if (isSafeStandLocation(world, tx, ty, tz)) {
                            safeSpot = new Location(world, tx + 0.5, ty, tz + 0.5);
                            break;
                        }
                    }
                    if (safeSpot != null) break;
                }
                if (safeSpot != null) break;
            }
            if (safeSpot != null) break;
        }

        if (safeSpot != null) {
            safeSpot.setPitch(current.getPitch());
            safeSpot.setYaw(current.getYaw());
            player.teleport(safeSpot);
            currentIndex = findClosestPathIndex(safeSpot);
            player.sendMessage(Component.text("✔ Teleported to a nearby safe spot!").color(NamedTextColor.GREEN));
        } else {
            // Fallback: teleport to the highest safe block at player's current X/Z
            int highestY = world.getHighestBlockYAt(px, pz);
            Location fallback = new Location(world, px + 0.5, highestY + 1.0, pz + 0.5);
            fallback.setPitch(current.getPitch());
            fallback.setYaw(current.getYaw());
            player.teleport(fallback);
            currentIndex = findClosestPathIndex(fallback);
            player.sendMessage(Component.text("✔ Teleported to surface safety!").color(NamedTextColor.GREEN));
        }
    }

    private int findClosestPathIndex(Location loc) {
        if (path.isEmpty()) return 0;
        int closestIndex = 0;
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double distSq = path.get(i).distanceSquared(loc);
            if (distSq < minDistSq) {
                minDistSq = distSq;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private boolean isProtectedItem(Material mat) {
        if (mat.isEdible()) return true;
        
        String name = mat.name();
        
        // Protect Weapons, Tools, & Combat/Survival Gear
        if (name.contains("SWORD") || name.contains("AXE") || name.contains("PICKAXE") 
                || name.contains("SHOVEL") || name.contains("HOE") || name.contains("HELMET") 
                || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") 
                || name.contains("SHIELD") || name.contains("BOW") || name.contains("CROSSBOW") 
                || name.contains("TRIDENT") || name.contains("MACE") || mat == Material.SHEARS 
                || mat == Material.FISHING_ROD || mat == Material.FLINT_AND_STEEL || mat == Material.BRUSH 
                || mat == Material.SPYGLASS || mat == Material.LEAD) {
            return true;
        }
        
        // Protect high-value survival & portable storage utility items
        if (mat == Material.TOTEM_OF_UNDYING || mat == Material.ENDER_CHEST 
                || name.contains("SHULKER_BOX")) {
            return true;
        }
        
        // Protect survival buckets & specialty fluids
        if (mat == Material.MILK_BUCKET || mat == Material.WATER_BUCKET 
                || mat == Material.LAVA_BUCKET || mat == Material.BUCKET 
                || mat == Material.HONEY_BOTTLE || mat == Material.POTION 
                || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION) {
            return true;
        }
        
        // Protect basic lighting utilities
        if (name.contains("TORCH") || name.contains("LANTERN") || name.contains("CAMPFIRE") 
                || mat == Material.GLOWSTONE || mat == Material.SEA_LANTERN) {
            return true;
        }
        
        return false;
    }
}

