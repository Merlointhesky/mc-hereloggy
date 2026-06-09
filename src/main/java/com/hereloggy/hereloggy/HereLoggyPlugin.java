package com.hereloggy.hereloggy;

import com.hereloggy.hereloggy.auraskills.AuraSkillsHelper;
import com.hereloggy.hereloggy.command.HereLoggyCommand;
import com.hereloggy.hereloggy.command.SetupWizardCommand;
import com.hereloggy.hereloggy.config.TreeConfigManager;
import com.hereloggy.hereloggy.config.TreeConfigUI;
import com.hereloggy.hereloggy.config.TreeConfigListener;
import com.hereloggy.hereloggy.hereroleplay.HereRolePlayHelper;
import com.hereloggy.hereloggy.listener.ChopListener;
import com.hereloggy.hereloggy.listener.SetupWizardListener;
import com.hereloggy.hereloggy.map.ScanManager;
import com.hereloggy.hereloggy.selection.SelectionManager;
import com.hereloggy.hereloggy.setup.SetupManager;
import com.hereloggy.hereloggy.task.ChopTaskManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class HereLoggyPlugin extends JavaPlugin {

    private static HereLoggyPlugin instance;
    private SelectionManager selectionManager;
    private final ChopTaskManager chopTaskManager = new ChopTaskManager();
    private final AuraSkillsHelper auraSkillsHelper = new AuraSkillsHelper();
    private final HereRolePlayHelper hereRolePlayHelper = new HereRolePlayHelper();
    private ScanManager scanManager;
    private SetupManager setupManager;
    private TreeConfigManager treeConfigManager;
    private TreeConfigUI treeConfigUI;
    private SetupWizardCommand setupWizardCommand;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers
        this.selectionManager = new SelectionManager(this);
        this.scanManager = new ScanManager(this);
        this.setupManager = new SetupManager(this);
        this.treeConfigManager = new TreeConfigManager(this);
        this.treeConfigUI = new TreeConfigUI(treeConfigManager);

        auraSkillsHelper.init();
        hereRolePlayHelper.init();

        // Create setup wizard command
        setupWizardCommand = new SetupWizardCommand(setupManager, this);

        // Register main command
        getCommand("hereloggy").setExecutor(new HereLoggyCommand(selectionManager, chopTaskManager, auraSkillsHelper, hereRolePlayHelper, scanManager, setupWizardCommand, treeConfigUI));

        // Register listeners
        getServer().getPluginManager().registerEvents(new ChopListener(selectionManager, chopTaskManager, scanManager, setupManager, treeConfigManager), this);
        getServer().getPluginManager().registerEvents(new SetupWizardListener(setupManager), this);
        getServer().getPluginManager().registerEvents(setupWizardCommand, this);
        getServer().getPluginManager().registerEvents(new TreeConfigListener(treeConfigUI, treeConfigManager), this);

        // Start setup timeout checker
        new BukkitRunnable() {
            @Override
            public void run() {
                setupWizardCommand.checkTimeouts();
            }
        }.runTaskTimer(this, 0, 20); // Check every second (20 ticks)

        getLogger().info("HereLoggy enabled successfully!");
    }

    @Override
    public void onDisable() {
        chopTaskManager.stopAllTasks();
        getLogger().info("HereLoggy disabled!");
    }

    public static HereLoggyPlugin getInstance() {
        return instance;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public ChopTaskManager getChopTaskManager() {
        return chopTaskManager;
    }

    public AuraSkillsHelper getAuraSkillsHelper() {
        return auraSkillsHelper;
    }

    public HereRolePlayHelper getHereRolePlayHelper() {
        return hereRolePlayHelper;
    }

    public SetupManager getSetupManager() {
        return setupManager;
    }

    public TreeConfigManager getTreeConfigManager() {
        return treeConfigManager;
    }

    public TreeConfigUI getTreeConfigUI() {
        return treeConfigUI;
    }

    public ScanManager getScanManager() {
        return scanManager;
    }
}

