package com.hereloggy.hereloggy.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

public class AreaScanner {

    public static ScanResult scan(Location pointA, Location pointB) {
        World world = pointA.getWorld();
        int minX = Math.min(pointA.getBlockX(), pointB.getBlockX());
        int maxX = Math.max(pointA.getBlockX(), pointB.getBlockX());
        int minZ = Math.min(pointA.getBlockZ(), pointB.getBlockZ());
        int maxZ = Math.max(pointA.getBlockZ(), pointB.getBlockZ());
        int minY = Math.min(pointA.getBlockY(), pointB.getBlockY());
        int maxY = Math.max(pointA.getBlockY(), pointB.getBlockY());
        int baseY = pointA.getBlockY();

        Map<String, BlockClassification> classifications = new HashMap<>();
        Map<String, Integer> groundYLevels = new HashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var result = classifyColumn(world, x, baseY, minY, maxY, z);
                classifications.put(x + "," + z, result.classification());
                if (result.groundY() != null) {
                    groundYLevels.put(x + "," + z, result.groundY());
                }
            }
        }

        return new ScanResult(pointA, pointB, classifications, groundYLevels);
    }

    private static ColumnResult classifyColumn(World world, int x, int baseY, int minY, int maxY, int z) {
        // Find the ground block near baseY or within the Y boundaries
        Block ground = findGroundBlock(world, x, baseY, minY, maxY, z);
        if (ground == null) {
            return new ColumnResult(BlockClassification.OBSTRUCTED, null);
        }

        int groundY = ground.getY();
        Block above1 = world.getBlockAt(x, groundY + 1, z);
        Block above2 = world.getBlockAt(x, groundY + 2, z);

        // Check if there is a log block directly on the ground (indicating a tree trunk base)
        if (isLog(above1.getType()) || isMangroveRoot(above1.getType())) {
            return new ColumnResult(BlockClassification.TREE_BASE, groundY);
        }

        // Check for door or gates in the two blocks above ground
        boolean hasDoorPassage = isOpenablePassage(above1.getType()) || isOpenablePassage(above2.getType());
        if (hasDoorPassage) {
            return new ColumnResult(BlockClassification.DOOR, groundY);
        }

        // To be walkable/passable, the space above ground must be clear (air or passable)
        if (isAirOrPassable(above1.getType()) && isAirOrPassable(above2.getType())) {
            return new ColumnResult(BlockClassification.PASSABLE, groundY);
        }

        return new ColumnResult(BlockClassification.OBSTRUCTED, groundY);
    }

    private record ColumnResult(BlockClassification classification, Integer groundY) {
    }

    private static Block findGroundBlock(World world, int x, int baseY, int minY, int maxY, int z) {
        // Check baseY first
        Block block = world.getBlockAt(x, baseY, z);
        if (isSolidGround(block)) {
            return block;
        }

        // Scan downwards from maxY to minY to find first solid ground block
        for (int y = maxY; y >= minY; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (isSolidGround(b)) {
                return b;
            }
        }

        // Fallback: scan downward from baseY down to 10 blocks below
        for (int y = baseY; y >= Math.max(0, baseY - 10); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (isSolidGround(b)) {
                return b;
            }
        }
        return null;
    }

    private static boolean isSolidGround(Block block) {
        Material material = block.getType();
        if (!material.isSolid() || material.isAir()) {
            return false;
        }
        // Logs/Leaves are not considered stable "ground" block for standing, but they can be chopped
        if (isLog(material) || isLeaves(material)) {
            return false;
        }
        // Exclude unsafe ground types
        return !isUnsafeGround(material);
    }

    private static boolean isUnsafeGround(Material material) {
        return switch (material) {
            case LAVA, LAVA_CAULDRON, WATER, WATER_CAULDRON, ICE, PACKED_ICE, BLUE_ICE,
                 MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE,
                 LANTERN, SOUL_LANTERN, TORCH, SOUL_TORCH, WALL_TORCH -> true;
            default -> false;
        };
    }

    private static boolean isAirOrPassable(Material material) {
        return material.isAir()
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || isLeaves(material)
                || isLog(material)
                || isMangroveRoot(material)
                || isOpenablePassage(material)
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.FERN
                || material == Material.LARGE_FERN;
    }

    private static boolean isOpenablePassage(Material material) {
        return Tag.DOORS.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material);
    }

    private static boolean isLog(Material material) {
        String name = material.name();
        return Tag.LOGS.isTagged(material) || name.contains("_LOG") || name.contains("_WOOD") || name.contains("_STEM") || name.contains("_HYPHAE");
    }

    private static boolean isLeaves(Material material) {
        String name = material.name();
        return Tag.LEAVES.isTagged(material) || name.contains("_LEAVES") || material == Material.NETHER_WART_BLOCK || material == Material.WARPED_WART_BLOCK || material == Material.SHROOMLIGHT;
    }

    private static boolean isMangroveRoot(Material material) {
        return material == Material.MANGROVE_ROOTS || material == Material.MUDDY_MANGROVE_ROOTS;
    }
}
