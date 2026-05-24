package com.hereloggy.hereloggy.config;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class PlayerTreeConfig {

    public enum ChoppingMethod {
        REACHABLE,
        WHOLE_TREE
    }

    private final String playerId;
    private ChoppingMethod choppingMethod;
    private final Map<Material, TreeSettings> settingsMap = new HashMap<>();
    private long lastModified;

    public PlayerTreeConfig(String playerId) {
        this.playerId = playerId;
        this.choppingMethod = ChoppingMethod.WHOLE_TREE;
        this.lastModified = System.currentTimeMillis();
        initializeDefaults();
    }

    private void initializeDefaults() {
        Material[] treeLogs = {
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM
        };

        for (Material log : treeLogs) {
            settingsMap.put(log, new TreeSettings(log));
        }
    }

    public String getPlayerId() {
        return playerId;
    }

    public ChoppingMethod getChoppingMethod() {
        return choppingMethod;
    }

    public void setChoppingMethod(ChoppingMethod choppingMethod) {
        this.choppingMethod = choppingMethod;
        this.lastModified = System.currentTimeMillis();
    }

    public void toggleChoppingMethod() {
        if (choppingMethod == ChoppingMethod.WHOLE_TREE) {
            choppingMethod = ChoppingMethod.REACHABLE;
        } else {
            choppingMethod = ChoppingMethod.WHOLE_TREE;
        }
        this.lastModified = System.currentTimeMillis();
    }

    public TreeSettings getTreeSettings(Material logType) {
        // Fallback/normalization for other variations of logs (wood, stripped, stems, hyphae, etc.)
        Material key = normalizeLogType(logType);
        return settingsMap.computeIfAbsent(key, k -> new TreeSettings(k));
    }

    public void setTreeSettings(Material logType, TreeSettings settings) {
        Material key = normalizeLogType(logType);
        settingsMap.put(key, settings);
        this.lastModified = System.currentTimeMillis();
    }

    public Map<Material, TreeSettings> getAllTreeSettings() {
        return settingsMap;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void resetToDefaults() {
        this.choppingMethod = ChoppingMethod.WHOLE_TREE;
        for (TreeSettings settings : settingsMap.values()) {
            settings.reset();
        }
        this.lastModified = System.currentTimeMillis();
    }

    public static Material normalizeLogType(Material material) {
        String name = material.name();
        if (name.contains("OAK")) {
            return Material.OAK_LOG;
        } else if (name.contains("SPRUCE")) {
            return Material.SPRUCE_LOG;
        } else if (name.contains("BIRCH")) {
            return Material.BIRCH_LOG;
        } else if (name.contains("JUNGLE")) {
            return Material.JUNGLE_LOG;
        } else if (name.contains("ACACIA")) {
            return Material.ACACIA_LOG;
        } else if (name.contains("DARK_OAK")) {
            return Material.DARK_OAK_LOG;
        } else if (name.contains("MANGROVE")) {
            return Material.MANGROVE_LOG;
        } else if (name.contains("CHERRY")) {
            return Material.CHERRY_LOG;
        } else if (name.contains("CRIMSON")) {
            return Material.CRIMSON_STEM;
        } else if (name.contains("WARPED")) {
            return Material.WARPED_STEM;
        }
        return material;
    }
}
