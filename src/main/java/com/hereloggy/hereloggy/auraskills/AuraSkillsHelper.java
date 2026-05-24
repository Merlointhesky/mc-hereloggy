package com.hereloggy.hereloggy.auraskills;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class AuraSkillsHelper {

    private boolean auraSkillsAvailable = false;

    public void init() {
        auraSkillsAvailable = Bukkit.getPluginManager().getPlugin("AuraSkills") != null;
    }

    public boolean isAvailable() {
        return auraSkillsAvailable;
    }

    public int getForagingLevel(Player player) {
        if (!auraSkillsAvailable) return 0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                return user.getSkillLevel(Skills.FORAGING);
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    public double getForagingFortune(Player player) {
        if (!auraSkillsAvailable) return 0.0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                // In AuraSkills, foraging double drops usually scale with level or specific stat.
                // We'll use foraging level * 1.5 as a simple fortune bonus estimation
                return user.getSkillLevel(Skills.FORAGING) * 1.5;
            }
        } catch (Exception e) {
            // ignore
        }
        return 0.0;
    }

    public void addForagingXp(Player player, double baseXp) {
        if (!auraSkillsAvailable) return;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null) {
                int level = getForagingLevel(player);
                double xpAmount = baseXp * (1.0 + level * 0.02);
                user.addSkillXp(Skills.FORAGING, xpAmount);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public double getBaseXpForLog(org.bukkit.Material logType) {
        org.bukkit.Material normalized = com.hereloggy.hereloggy.config.PlayerTreeConfig.normalizeLogType(logType);
        return switch (normalized) {
            case OAK_LOG -> 10.0;
            case SPRUCE_LOG -> 12.0;
            case BIRCH_LOG -> 10.0;
            case JUNGLE_LOG -> 15.0;
            case ACACIA_LOG -> 15.0;
            case DARK_OAK_LOG -> 15.0;
            case MANGROVE_LOG -> 15.0;
            case CHERRY_LOG -> 12.0;
            case CRIMSON_STEM -> 20.0;
            case WARPED_STEM -> 20.0;
            default -> 10.0;
        };
    }
}
