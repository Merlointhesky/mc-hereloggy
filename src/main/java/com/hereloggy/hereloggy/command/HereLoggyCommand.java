package com.hereloggy.hereloggy.command;

import com.hereloggy.hereloggy.HereLoggyPlugin;
import com.hereloggy.hereloggy.auraskills.AuraSkillsHelper;
import com.hereloggy.hereloggy.config.TreeConfigUI;
import com.hereloggy.hereloggy.map.ScanManager;
import com.hereloggy.hereloggy.map.ScanResult;
import com.hereloggy.hereloggy.path.PathGenerator;
import com.hereloggy.hereloggy.selection.SelectionManager;
import com.hereloggy.hereloggy.task.ChopTask;
import com.hereloggy.hereloggy.task.ChopTaskManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HereLoggyCommand implements CommandExecutor {

    private final SelectionManager selectionManager;
    private final ChopTaskManager chopTaskManager;
    private final AuraSkillsHelper auraSkillsHelper;
    private final ScanManager scanManager;
    private final SetupWizardCommand setupWizardCommand;
    private final TreeConfigUI treeConfigUI;

    public HereLoggyCommand(SelectionManager selectionManager, ChopTaskManager chopTaskManager,
                            AuraSkillsHelper auraSkillsHelper, ScanManager scanManager,
                            SetupWizardCommand setupWizardCommand, TreeConfigUI treeConfigUI) {
        this.selectionManager = selectionManager;
        this.chopTaskManager = chopTaskManager;
        this.auraSkillsHelper = auraSkillsHelper;
        this.scanManager = scanManager;
        this.setupWizardCommand = setupWizardCommand;
        this.treeConfigUI = treeConfigUI;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /hereloggy <start|stop|restart|clear|setup|config>")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start" -> {
                if (chopTaskManager.isChopping(player)) {
                    player.sendMessage(Component.text("Auto-chopping is already enabled!")
                            .color(NamedTextColor.YELLOW));
                    return true;
                }

                if (!selectionManager.hasCompleteSelection(player.getUniqueId())) {
                    player.sendMessage(Component.text("You must set two points first! Shift-right-click with an axe to set Point A and Point B.")
                            .color(NamedTextColor.RED));
                    return true;
                }

                if (!scanManager.hasScan(player.getUniqueId())) {
                    player.sendMessage(Component.text("Scanning area... Please wait.")
                            .color(NamedTextColor.GREEN));
                    scanManager.scanAreaAsync(player.getUniqueId(),
                            selectionManager.getPointA(player.getUniqueId()),
                            selectionManager.getPointB(player.getUniqueId()),
                            result -> startChopping(player, result));
                    return true;
                }

                ScanResult scanResult = scanManager.getScanResult(player.getUniqueId());
                startChopping(player, scanResult);
            }
            case "stop" -> {
                if (!chopTaskManager.isChopping(player)) {
                    player.sendMessage(Component.text("Auto-chopping is not enabled!")
                            .color(NamedTextColor.YELLOW));
                } else {
                    chopTaskManager.stopTask(player);
                    player.sendMessage(Component.text("Auto-chopping disabled.")
                            .color(NamedTextColor.GREEN));
                }
            }
            case "restart" -> {
                if (!chopTaskManager.hasLastStop(player)) {
                    player.sendMessage(Component.text("No paused session to restart. Use /hereloggy start instead.")
                            .color(NamedTextColor.YELLOW));
                    return true;
                }

                if (!selectionManager.hasCompleteSelection(player.getUniqueId())) {
                    player.sendMessage(Component.text("Selection missing! Please reselect the area.")
                            .color(NamedTextColor.RED));
                    return true;
                }

                if (!scanManager.hasScan(player.getUniqueId())) {
                    player.sendMessage(Component.text("Scanning area... Please wait.")
                            .color(NamedTextColor.GREEN));
                    scanManager.scanAreaAsync(player.getUniqueId(),
                            selectionManager.getPointA(player.getUniqueId()),
                            selectionManager.getPointB(player.getUniqueId()),
                            result -> restartChopping(player, result));
                    return true;
                }

                ScanResult scanResult = scanManager.getScanResult(player.getUniqueId());
                restartChopping(player, scanResult);
            }
            case "clear" -> {
                selectionManager.clearSelection(player.getUniqueId());
                scanManager.clearScan(player.getUniqueId());
                chopTaskManager.clearLastStop(player);
                HereLoggyPlugin.getInstance().getSetupManager().clearSetupConfig(player.getUniqueId());
                player.sendMessage(Component.text("Selection and setup configuration cleared.")
                        .color(NamedTextColor.GREEN));
            }
            case "setup" -> {
                if (setupWizardCommand != null) {
                    setupWizardCommand.onCommand(player);
                } else {
                    player.sendMessage(Component.text("Setup wizard is not available.")
                            .color(NamedTextColor.RED));
                }
            }
            case "config" -> {
                if (treeConfigUI != null) {
                    treeConfigUI.openMainMenu(player);
                } else {
                    player.sendMessage(Component.text("Tree configuration is not available.")
                            .color(NamedTextColor.RED));
                }
            }
            default -> player.sendMessage(Component.text("Usage: /hereloggy <start|stop|restart|clear|setup|config>")
                    .color(NamedTextColor.YELLOW));
        }

        return true;
    }

    private void startChopping(Player player, ScanResult scanResult) {
        List<Location> path = PathGenerator.generateSafePath(scanResult);

        if (path.isEmpty()) {
            player.sendMessage(Component.text("The selected area has no walkable blocks.")
                    .color(NamedTextColor.RED));
            return;
        }

        // Order the path starting from the end closest to the player's current location
        Location currentLoc = player.getLocation();
        double distToStart = currentLoc.distanceSquared(path.get(0));
        double distToEnd = currentLoc.distanceSquared(path.get(path.size() - 1));
        if (distToEnd < distToStart) {
            java.util.Collections.reverse(path);
        }

        ChopTask task = new ChopTask(HereLoggyPlugin.getInstance(), player, path, auraSkillsHelper, scanResult);
        task.setCurrentIndex(0);
        chopTaskManager.startTask(player, task);
        chopTaskManager.clearLastStop(player);

        player.sendMessage(Component.text("Auto-chopping enabled! Walking ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(String.valueOf(path.size())).color(NamedTextColor.YELLOW))
                .append(Component.text(" blocks from index 1.").color(NamedTextColor.GREEN)));
    }


    private void restartChopping(Player player, ScanResult scanResult) {
        List<Location> path = PathGenerator.generateSafePath(scanResult);

        if (path.isEmpty()) {
            player.sendMessage(Component.text("The selected area has no walkable blocks.")
                    .color(NamedTextColor.RED));
            return;
        }

        int lastIndex = chopTaskManager.getLastStopIndex(player);
        ChopTask task = new ChopTask(HereLoggyPlugin.getInstance(), player, path, auraSkillsHelper, scanResult);
        if (lastIndex >= 0 && lastIndex < path.size()) {
            task.setCurrentIndex(lastIndex);
        }
        chopTaskManager.startTask(player, task);
        chopTaskManager.clearLastStop(player);

        player.sendMessage(Component.text("Auto-chopping restarted from block ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(String.valueOf(task.getCurrentIndex() + 1)).color(NamedTextColor.YELLOW))
                .append(Component.text(" of ").color(NamedTextColor.GREEN))
                .append(Component.text(String.valueOf(path.size())).color(NamedTextColor.YELLOW))
                .append(Component.text(".").color(NamedTextColor.GREEN)));
    }
}
