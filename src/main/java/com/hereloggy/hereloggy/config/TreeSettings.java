package com.hereloggy.hereloggy.config;

import org.bukkit.Material;

public class TreeSettings {
    private final Material logType;
    private boolean enabled;
    private boolean replantEnabled;
    private boolean junkEnabled; // true = trash chest, false = keep chest

    public TreeSettings(Material logType) {
        this.logType = logType;
        this.enabled = true;
        this.replantEnabled = true;
        this.junkEnabled = false; // Default to keep
    }

    public TreeSettings(Material logType, boolean enabled, boolean replantEnabled, boolean junkEnabled) {
        this.logType = logType;
        this.enabled = enabled;
        this.replantEnabled = replantEnabled;
        this.junkEnabled = junkEnabled;
    }

    public Material getLogType() {
        return logType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggleEnabled() {
        this.enabled = !this.enabled;
    }

    public boolean isReplantEnabled() {
        return replantEnabled;
    }

    public void setReplantEnabled(boolean enabled) {
        this.replantEnabled = enabled;
    }

    public void toggleReplant() {
        this.replantEnabled = !this.replantEnabled;
    }

    public boolean isJunkEnabled() {
        return junkEnabled;
    }

    public void setJunkEnabled(boolean enabled) {
        this.junkEnabled = enabled;
    }

    public void toggleJunk() {
        this.junkEnabled = !this.junkEnabled;
    }

    public void reset() {
        this.enabled = true;
        this.replantEnabled = true;
        this.junkEnabled = false;
    }
}
