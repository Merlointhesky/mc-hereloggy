package com.hereloggy.hereloggy.map;

import org.bukkit.Location;
import java.util.Map;

public class ScanResult {

    private final Location pointA;
    private final Location pointB;
    private final Map<String, BlockClassification> classifications;
    private final Map<String, Integer> groundYLevels;

    public ScanResult(Location pointA, Location pointB,
                      Map<String, BlockClassification> classifications,
                      Map<String, Integer> groundYLevels) {
        this.pointA = pointA;
        this.pointB = pointB;
        this.classifications = classifications;
        this.groundYLevels = groundYLevels;
    }

    public Location getPointA() {
        return pointA;
    }

    public Location getPointB() {
        return pointB;
    }

    public Map<String, BlockClassification> getClassifications() {
        return classifications;
    }

    public Map<String, Integer> getGroundYLevels() {
        return groundYLevels;
    }

    public BlockClassification getClassification(int x, int z) {
        return classifications.getOrDefault(x + "," + z, BlockClassification.OBSTRUCTED);
    }

    public boolean isObstructed(int x, int z) {
        return getClassification(x, z) == BlockClassification.OBSTRUCTED;
    }

    public boolean isPassable(int x, int z) {
        BlockClassification c = getClassification(x, z);
        return c == BlockClassification.PASSABLE || c == BlockClassification.DOOR;
    }

    public boolean isTreeBase(int x, int z) {
        return getClassification(x, z) == BlockClassification.TREE_BASE;
    }

    public int getGroundY(int x, int z) {
        return groundYLevels.getOrDefault(x + "," + z, pointA.getBlockY());
    }
}
