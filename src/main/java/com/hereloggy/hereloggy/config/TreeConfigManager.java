package com.hereloggy.hereloggy.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TreeConfigManager {
    private final Plugin plugin;
    private final File configDir;
    private final Map<UUID, PlayerTreeConfig> playerConfigs = new HashMap<>();

    public TreeConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.configDir = new File(plugin.getDataFolder(), "player-configs");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
    }

    public PlayerTreeConfig getPlayerConfig(UUID playerId) {
        if (!playerConfigs.containsKey(playerId)) {
            loadPlayerConfig(playerId);
        }
        return playerConfigs.get(playerId);
    }

    public void loadPlayerConfig(UUID playerId) {
        File file = new File(configDir, playerId + ".yml");
        PlayerTreeConfig config = new PlayerTreeConfig(playerId.toString());

        if (file.exists()) {
            FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);

            if (yaml.contains("choppingMethod")) {
                try {
                    config.setChoppingMethod(PlayerTreeConfig.ChoppingMethod.valueOf(yaml.getString("choppingMethod")));
                } catch (IllegalArgumentException ignored) {}
            }

            if (yaml.contains("treeSettings")) {
                org.bukkit.configuration.ConfigurationSection treeSection = yaml.getConfigurationSection("treeSettings");
                for (String logName : treeSection.getKeys(false)) {
                    try {
                        Material logType = Material.valueOf(logName);
                        org.bukkit.configuration.ConfigurationSection settingsSection = treeSection.getConfigurationSection(logName);

                        boolean enabled = settingsSection.getBoolean("enabled", true);
                        boolean replant = settingsSection.getBoolean("replantEnabled", true);
                        boolean junk = settingsSection.getBoolean("junkEnabled", false);

                        TreeSettings settings = new TreeSettings(logType, enabled, replant, junk);
                        config.setTreeSettings(logType, settings);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown tree log type in config: " + logName);
                    }
                }
            }

            if (yaml.contains("lastModified")) {
                config.setLastModified(yaml.getLong("lastModified"));
            }
        }

        playerConfigs.put(playerId, config);
    }

    public TreeSettings getTreeSettings(UUID playerId, Material logType) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        return config.getTreeSettings(logType);
    }

    public boolean isTreeEnabled(UUID playerId, Material logType) {
        return getTreeSettings(playerId, logType).isEnabled();
    }

    public boolean isReplantEnabled(UUID playerId, Material logType) {
        return getTreeSettings(playerId, logType).isReplantEnabled();
    }

    public boolean isJunkEnabled(UUID playerId, Material logType) {
        return getTreeSettings(playerId, logType).isJunkEnabled();
    }

    public void toggleEnabled(UUID playerId, Material logType) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        config.getTreeSettings(logType).toggleEnabled();
        saveConfiguration(playerId);
    }

    public void toggleReplant(UUID playerId, Material logType) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        config.getTreeSettings(logType).toggleReplant();
        saveConfiguration(playerId);
    }

    public void toggleJunk(UUID playerId, Material logType) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        config.getTreeSettings(logType).toggleJunk();
        saveConfiguration(playerId);
    }

    public void toggleChoppingMethod(UUID playerId) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        config.toggleChoppingMethod();
        saveConfiguration(playerId);
    }

    public void resetToDefaults(UUID playerId) {
        PlayerTreeConfig config = getPlayerConfig(playerId);
        config.resetToDefaults();
        saveConfiguration(playerId);
    }

    public void saveConfiguration(UUID playerId) {
        PlayerTreeConfig config = playerConfigs.get(playerId);
        if (config == null) return;

        File file = new File(configDir, playerId + ".yml");
        FileConfiguration yaml = new YamlConfiguration();

        yaml.set("playerId", config.getPlayerId());
        yaml.set("choppingMethod", config.getChoppingMethod().name());
        yaml.set("lastModified", config.getLastModified());

        for (Map.Entry<Material, TreeSettings> entry : config.getAllTreeSettings().entrySet()) {
            String path = "treeSettings." + entry.getKey().name();
            TreeSettings settings = entry.getValue();
            yaml.set(path + ".enabled", settings.isEnabled());
            yaml.set(path + ".replantEnabled", settings.isReplantEnabled());
            yaml.set(path + ".junkEnabled", settings.isJunkEnabled());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save tree configuration for " + playerId + ": " + e.getMessage());
        }
    }

    public void clearPlayerConfig(UUID playerId) {
        playerConfigs.remove(playerId);
        File file = new File(configDir, playerId + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }
}
