package com.hereloggy.hereloggy.listener;

import com.hereloggy.hereloggy.setup.SetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SetupWizardListener implements Listener {

    private final SetupManager setupManager;

    public SetupWizardListener(SetupManager setupManager) {
        this.setupManager = setupManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!setupManager.isInSetup(uuid)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Verify the clicked block is a storage container
        if (!(block.getState() instanceof Container)) {
            return; // Not a chest or barrel
        }

        // Verify player is sneaking and holding an axe
        if (!player.isSneaking()) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.getType().name().contains("AXE") || held.getType().name().contains("PICKAXE")) {
            return;
        }

        event.setCancelled(true);
        Location loc = block.getLocation();
        int step = setupManager.getCurrentStep(uuid);

        if (step == 0) {
            // Trash Chest
            setupManager.setTrashChest(uuid, loc);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.2f);
            player.sendMessage(Component.text("✔ Trash deposit chest set successfully!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Step 2: Shift-Right-Click the container where you want to deposit valuable WOOD (logs).")
                    .color(NamedTextColor.GREEN));
        } else if (step == 1) {
            // Keep Chest
            setupManager.setKeepChest(uuid, loc);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.2f);
            player.sendMessage(Component.text("✔ Wood logs deposit chest set successfully!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Step 3: Shift-Right-Click the container where you store SPARE AXES, or type 'skip' in chat to bypass supply restocking.")
                    .color(NamedTextColor.GREEN));
        } else if (step == 2) {
            // Tool Supply Chest
            setupManager.setToolSupplyChest(uuid, loc);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.2f);
            player.sendMessage(Component.text("✔ Tool supply chest set successfully!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("  Step 4: Type an axe durability threshold (default: 10) below which the plugin swaps axes, or type 'skip' in chat to keep default.")
                    .color(NamedTextColor.GREEN));
        }
    }
}
