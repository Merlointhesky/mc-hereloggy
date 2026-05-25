package com.hereloggy.hereloggy.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class TreeConfigListener implements Listener {

    private final TreeConfigUI configUI;
    private final TreeConfigManager configManager;

    public TreeConfigListener(TreeConfigUI configUI, TreeConfigManager configManager) {
        this.configUI = configUI;
        this.configManager = configManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component titleComponent = event.getView().title();
        String titleStr = PlainTextComponentSerializer.plainText().serialize(titleComponent);
        if (!titleStr.contains("HereLoggy")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (titleStr.contains("Tree Settings")) {
            handleMainMenuClick(player, clicked, event.getSlot());
        } else if (titleStr.contains("Tree Options")) {
            handleSettingsMenuClick(player, clicked, event.getSlot());
        }
    }

    private void handleMainMenuClick(Player player, ItemStack clicked, int slot) {
        Material material = clicked.getType();

        // Check if clicked tree log type (slot 9 to 18)
        if (isTreeLog(material)) {
            configUI.openTreeSettingsMenu(player, material);
        }
        // Check for chopping method toggle (slot 22)
        else if (slot == 22) {
            configManager.toggleChoppingMethod(player.getUniqueId());
            PlayerTreeConfig config = configManager.getPlayerConfig(player.getUniqueId());
            player.sendMessage(Component.text("Chopping method changed to: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(config.getChoppingMethod().name())
                            .color(NamedTextColor.YELLOW)));
            configUI.openMainMenu(player);
        }
        // Check for remove bees global toggle (slot 20)
        else if (slot == 20) {
            configManager.toggleRemoveBees(player.getUniqueId());
            boolean removeBees = configManager.isRemoveBeesEnabled(player.getUniqueId());
            player.sendMessage(Component.text("Hive/Bee removal is now ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(removeBees ? "ENABLED" : "DISABLED")
                            .color(removeBees ? NamedTextColor.GREEN : NamedTextColor.RED)));
            configUI.openMainMenu(player);
        }
    }

    private void handleSettingsMenuClick(Player player, ItemStack clicked, int slot) {
        Material material = clicked.getType();

        Material logType = extractLogTypeFromSettings(player);
        if (logType == null) return;

        // Slot 11: Chopping Status enabled/disabled toggle
        if (slot == 11) {
            configManager.toggleEnabled(player.getUniqueId(), logType);
            boolean enabled = configManager.isTreeEnabled(player.getUniqueId(), logType);
            player.sendMessage(Component.text("Chopping for " + TreeConfigUI.getTreeDisplayName(logType) + " is now ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(enabled ? "ENABLED" : "DISABLED")
                            .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
            configUI.openTreeSettingsMenu(player, logType);
        }
        // Slot 13: Replanting toggle
        else if (slot == 13) {
            configManager.toggleReplant(player.getUniqueId(), logType);
            boolean replant = configManager.isReplantEnabled(player.getUniqueId(), logType);
            player.sendMessage(Component.text("Replanting for " + TreeConfigUI.getTreeDisplayName(logType) + " is now ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(replant ? "ENABLED" : "DISABLED")
                            .color(replant ? NamedTextColor.GREEN : NamedTextColor.RED)));
            configUI.openTreeSettingsMenu(player, logType);
        }
        // Slot 15: Junk routing toggle
        else if (slot == 15) {
            configManager.toggleJunk(player.getUniqueId(), logType);
            boolean junk = configManager.isJunkEnabled(player.getUniqueId(), logType);
            player.sendMessage(Component.text("Routing for " + TreeConfigUI.getTreeDisplayName(logType) + " set to: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(junk ? "TRASH CHEST" : "KEEP CHEST")
                            .color(junk ? NamedTextColor.RED : NamedTextColor.GREEN)));
            configUI.openTreeSettingsMenu(player, logType);
        }
        // Slot 17: Proactive planting toggle
        else if (slot == 17) {
            configManager.toggleProactivePlant(player.getUniqueId(), logType);
            boolean proactive = configManager.isProactivePlantEnabled(player.getUniqueId(), logType);
            player.sendMessage(Component.text("Proactive Planting for " + TreeConfigUI.getTreeDisplayName(logType) + " is now ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(proactive ? "ENABLED" : "DISABLED")
                            .color(proactive ? NamedTextColor.GREEN : NamedTextColor.RED)));
            configUI.openTreeSettingsMenu(player, logType);
        }
        // Slot 26: Back Arrow button
        else if (slot == 26 || material == Material.ARROW) {
            configUI.openMainMenu(player);
        }
    }

    private Material extractLogTypeFromSettings(Player player) {
        ItemStack titleItem = player.getOpenInventory().getTopInventory().getItem(4);
        if (titleItem != null && isTreeLog(titleItem.getType())) {
            return titleItem.getType();
        }
        return null;
    }

    private boolean isTreeLog(Material material) {
        return material == Material.OAK_LOG || material == Material.SPRUCE_LOG ||
               material == Material.BIRCH_LOG || material == Material.JUNGLE_LOG ||
               material == Material.ACACIA_LOG || material == Material.DARK_OAK_LOG ||
               material == Material.MANGROVE_LOG || material == Material.CHERRY_LOG ||
               material == Material.CRIMSON_STEM || material == Material.WARPED_STEM;
    }
}
