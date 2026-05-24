package com.hereloggy.hereloggy.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TreeConfigUI {
    private final TreeConfigManager configManager;
    private static final String MAIN_MENU_TITLE = "HereLoggy - Tree Settings";
    private static final String SETTINGS_MENU_TITLE = "HereLoggy - Tree Options";

    public TreeConfigUI(TreeConfigManager configManager) {
        this.configManager = configManager;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(MAIN_MENU_TITLE)
                .color(NamedTextColor.GOLD));

        PlayerTreeConfig config = configManager.getPlayerConfig(player.getUniqueId());

        // Slots 9 to 18: Tree Logs
        int slot = 9;
        Material[] treeLogs = {
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM
        };

        for (Material log : treeLogs) {
            addTreeItem(inventory, slot++, log, player.getUniqueId());
        }

        // Slot 22: Chopping Method Toggle
        PlayerTreeConfig.ChoppingMethod method = config.getChoppingMethod();
        ItemStack methodItem = new ItemStack(method == PlayerTreeConfig.ChoppingMethod.WHOLE_TREE ? Material.GOLDEN_AXE : Material.IRON_AXE);
        ItemMeta meta = methodItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Chopping Method")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Current: " + (method == PlayerTreeConfig.ChoppingMethod.WHOLE_TREE ? "WHOLE TREE" : "REACHABLE"))
                    .color(method == PlayerTreeConfig.ChoppingMethod.WHOLE_TREE ? NamedTextColor.GREEN : NamedTextColor.AQUA));
            lore.add(Component.empty());
            lore.add(Component.text("Click to toggle chopping style.").color(NamedTextColor.GRAY));
            lore.add(Component.text("Reachable: Only wood within block reach height.").color(NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Whole Tree: Traverses recursively (radial 2 adj).").color(NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            methodItem.setItemMeta(meta);
        }
        inventory.setItem(22, methodItem);

        player.openInventory(inventory);
    }

    public void openTreeSettingsMenu(Player player, Material logType) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(SETTINGS_MENU_TITLE)
                .color(NamedTextColor.GOLD));

        TreeSettings settings = configManager.getTreeSettings(player.getUniqueId(), logType);

        // Slot 4: Title Item
        ItemStack titleItem = new ItemStack(logType);
        ItemMeta titleMeta = titleItem.getItemMeta();
        if (titleMeta != null) {
            titleMeta.displayName(Component.text(getTreeDisplayName(logType))
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
            titleItem.setItemMeta(titleMeta);
        }
        inventory.setItem(4, titleItem);

        // Slot 11: Enabled Toggle
        ItemStack enabledItem = createToggleItem(logType, "Chopping Status",
                settings.isEnabled(), "Enable/Disable chopping this tree type");
        inventory.setItem(11, enabledItem);

        // Slot 13: Replanting Toggle
        Material saplingMat = getSaplingMaterial(logType);
        ItemStack replantItem = createToggleItem(saplingMat, "Replant Saplings",
                settings.isReplantEnabled(), "Enable/Disable sapling replanting for this tree");
        inventory.setItem(13, replantItem);

        // Slot 15: Junk/Keep Toggle
        String junkLabel = settings.isJunkEnabled() ? "Route to Trash" : "Route to Keep";
        Material junkMaterial = settings.isJunkEnabled() ? Material.LAVA_BUCKET : Material.WATER_BUCKET;
        ItemStack junkItem = createToggleItem(junkMaterial, junkLabel,
                settings.isJunkEnabled(), "Junk (dump) or Keep (store) wood of this type");
        inventory.setItem(15, junkItem);

        // Slot 26: Back Button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Component.text("Back to Trees").color(NamedTextColor.YELLOW));
            backItem.setItemMeta(backMeta);
        }
        inventory.setItem(26, backItem);

        player.openInventory(inventory);
    }

    private void addTreeItem(Inventory inventory, int slot, Material logType, UUID playerId) {
        TreeSettings settings = configManager.getTreeSettings(playerId, logType);
        ItemStack item = new ItemStack(logType);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(getTreeDisplayName(logType))
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to configure settings").color(NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Chopping: " + (settings.isEnabled() ? "✓" : "✗"))
                    .color(settings.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Replanting: " + (settings.isReplantEnabled() ? "✓" : "✗"))
                    .color(settings.isReplantEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Routing: " + (settings.isJunkEnabled() ? "TRASH CHEST" : "KEEP CHEST"))
                    .color(settings.isJunkEnabled() ? NamedTextColor.RED : NamedTextColor.GREEN));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private ItemStack createToggleItem(Material material, String name, boolean enabled, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(description).color(NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Status: " + (enabled ? "ENABLED ✓" : "DISABLED ✗"))
                    .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Component.text("Click to toggle").color(NamedTextColor.GRAY));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String getTreeDisplayName(Material logType) {
        return switch (logType) {
            case OAK_LOG -> "Oak Tree";
            case SPRUCE_LOG -> "Spruce Tree";
            case BIRCH_LOG -> "Birch Tree";
            case JUNGLE_LOG -> "Jungle Tree";
            case ACACIA_LOG -> "Acacia Tree";
            case DARK_OAK_LOG -> "Dark Oak Tree";
            case MANGROVE_LOG -> "Mangrove Tree";
            case CHERRY_LOG -> "Cherry Tree";
            case CRIMSON_STEM -> "Crimson Tree (Nether)";
            case WARPED_STEM -> "Warped Tree (Nether)";
            default -> logType.name();
        };
    }

    public static Material getSaplingMaterial(Material logType) {
        return switch (logType) {
            case OAK_LOG -> Material.OAK_SAPLING;
            case SPRUCE_LOG -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG -> Material.DARK_OAK_SAPLING;
            case MANGROVE_LOG -> Material.MANGROVE_PROPAGULE;
            case CHERRY_LOG -> Material.CHERRY_SAPLING;
            case CRIMSON_STEM -> Material.CRIMSON_FUNGUS;
            case WARPED_STEM -> Material.WARPED_FUNGUS;
            default -> Material.OAK_SAPLING;
        };
    }
}
