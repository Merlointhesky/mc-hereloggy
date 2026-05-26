package com.hereloggy.hereloggy.listener;

import com.hereloggy.hereloggy.config.TreeConfigManager;
import com.hereloggy.hereloggy.map.ScanManager;
import com.hereloggy.hereloggy.selection.SelectionManager;
import com.hereloggy.hereloggy.setup.SetupManager;
import com.hereloggy.hereloggy.task.ChopTask;
import com.hereloggy.hereloggy.task.ChopTaskManager;
import org.bukkit.event.entity.EntityDamageEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ChopListener implements Listener {

    private final SelectionManager selectionManager;
    private final ChopTaskManager chopTaskManager;
    private final ScanManager scanManager;
    private final SetupManager setupManager;
    private final TreeConfigManager treeConfigManager;

    public ChopListener(SelectionManager selectionManager, ChopTaskManager chopTaskManager,
                        ScanManager scanManager, SetupManager setupManager, TreeConfigManager treeConfigManager) {
        this.selectionManager = selectionManager;
        this.chopTaskManager = chopTaskManager;
        this.scanManager = scanManager;
        this.setupManager = setupManager;
        this.treeConfigManager = treeConfigManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        treeConfigManager.loadPlayerConfig(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !isAxe(item.getType())) return;

        if (event.getClickedBlock() == null) return;

        // Skip selection when in setup wizard
        if (setupManager.isInSetup(player.getUniqueId())) {
            return;
        }

        Location clicked = event.getClickedBlock().getLocation();

        if (selectionManager.getPointA(player.getUniqueId()) == null) {
            selectionManager.setPointA(player.getUniqueId(), clicked);
            player.sendMessage(Component.text("Point A set at ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(formatLocation(clicked)).color(NamedTextColor.YELLOW))
                    .append(Component.text(". Shift-click again to set Point B.").color(NamedTextColor.GREEN)));
        } else if (selectionManager.getPointB(player.getUniqueId()) == null) {
            selectionManager.setPointB(player.getUniqueId(), clicked);
            player.sendMessage(Component.text("Point B set at ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(formatLocation(clicked)).color(NamedTextColor.YELLOW))
                    .append(Component.text(". Scanning area...").color(NamedTextColor.GREEN)));

            scanManager.scanAreaAsync(player.getUniqueId(),
                    selectionManager.getPointA(player.getUniqueId()),
                    selectionManager.getPointB(player.getUniqueId()),
                    result -> {
                        player.sendMessage(Component.text("Area mapped and trees discovered! Ready to ")
                                .color(NamedTextColor.GREEN)
                                .append(Component.text("/hereloggy start").color(NamedTextColor.YELLOW))
                                .append(Component.text("!").color(NamedTextColor.GREEN)));
                    });
        } else {
            selectionManager.clearSelection(player.getUniqueId());
            selectionManager.setPointA(player.getUniqueId(), clicked);
            player.sendMessage(Component.text("Selection reset. Point A set at ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(formatLocation(clicked)).color(NamedTextColor.YELLOW))
                    .append(Component.text(". Shift-click again to set Point B.").color(NamedTextColor.GREEN)));
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        chopTaskManager.stopTask(player);
        chopTaskManager.stopAutoDefense(player, true);
        scanManager.clearScan(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (chopTaskManager.hasAutoDefense(player)) {
            com.hereloggy.hereloggy.task.AutoDefenseTask defenseTask = chopTaskManager.getAutoDefenseTask(player);
            if (defenseTask != null) {
                Location expected = defenseTask.getExpectedTeleportLocation();

                // If it's a plugin-driven teleport/rotation, ignore it
                if (expected != null && expected.getWorld() == event.getTo().getWorld()) {
                    double distSq = expected.distanceSquared(event.getTo());
                    // A tiny threshold handles floating-point inaccuracies
                    if (distSq < 0.05) {
                        defenseTask.clearExpectedTeleportLocation();
                        return;
                    }
                }

                // Check if they actually moved (e.g. moved X, Y, or Z by > 0.05 blocks)
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getWorld() != to.getWorld() || 
                    Math.abs(from.getX() - to.getX()) > 0.05 || 
                    Math.abs(from.getY() - to.getY()) > 0.05 || 
                    Math.abs(from.getZ() - to.getZ()) > 0.05) {

                    // Manual movement detected! Stop auto defense
                    chopTaskManager.stopAutoDefense(player, false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;

        if (chopTaskManager.isChopping(player)) {
            ChopTask task = chopTaskManager.getActiveTask(player);
            if (task != null) {
                task.handleSuffocationRescue();
            }
        }
    }

    private boolean isAxe(Material material) {
        return switch (material) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
