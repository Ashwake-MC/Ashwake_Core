package com.ashwake.core.client.minimap;

import com.ashwake.core.AshwakeCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinimapClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int[] WAYPOINT_COLORS = new int[]{
            0xFFE0C78D,
            0xFF8EB57A,
            0xFF7AB0C8,
            0xFFD88B68,
            0xFFC58FBB,
            0xFFF0D864
    };

    private static final List<MinimapWaypoint> WAYPOINTS = new ArrayList<>();
    private static String currentWorldKey = "";
    private static MinimapWorldMap worldMap;
    private static int nextWaypointNumber = 1;
    private static int zoomIndex = 2; // Default 1.0x
    private static final float[] ZOOM_LEVELS = {0.25F, 0.5F, 1.0F, 2.0F, 4.0F};

    private static MinimapTexture mapTexture;
    private static int saveCounter = 0;
    private static int scanCounter = 0;

    private MinimapClientState() {
    }

    public static MinimapTexture getMapTexture() {
        if (mapTexture == null) {
            mapTexture = new MinimapTexture("ashwake_core_minimap", 512, 512);
        }
        return mapTexture;
    }

    public static void tick(Minecraft minecraft) {
        syncContext(minecraft);
        if (worldMap != null && ++saveCounter >= 1200) { // Save every 60 seconds
            worldMap.saveAll();
            saveCounter = 0;
        }

        // Periodic chunk scanning (every 0.5 seconds)
        if (minecraft != null && minecraft.player != null && minecraft.level != null && worldMap != null && ++scanCounter >= 10) {
            int px = (int) minecraft.player.getX() >> 4;
            int pz = (int) minecraft.player.getZ() >> 4;
            
            // Scan 7x7 area around player (enough for even fast elytra flight)
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    MinimapRenderUtil.scanChunk(minecraft, minecraft.level, px + dx, pz + dz);
                }
            }
            scanCounter = 0;
        }
    }

    public static MinimapWorldMap getWorldMap() {
        return worldMap;
    }

    public static void toggleMapScreen(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (minecraft.screen instanceof MinimapScreen) {
            minecraft.setScreen(null);
            return;
        }

        if (minecraft.screen == null) {
            minecraft.setScreen(new MinimapScreen());
        }
    }

    public static void zoomIn() {
        if (zoomIndex < ZOOM_LEVELS.length - 1) {
            zoomIndex++;
        }
    }

    public static void zoomOut() {
        if (zoomIndex > 0) {
            zoomIndex--;
        }
    }

    public static int getBlocksPerCell() {
        float zoom = ZOOM_LEVELS[zoomIndex];
        return zoom < 1.0F ? Math.round(1.0F / zoom) : 1;
    }

    public static int getCellSize() {
        float zoom = ZOOM_LEVELS[zoomIndex];
        return zoom > 1.0F ? Math.round(zoom) : 1;
    }

    public static List<MinimapWaypoint> getWaypoints(Minecraft minecraft) {
        syncContext(minecraft);
        return List.copyOf(WAYPOINTS);
    }

    public static List<MinimapWaypoint> getWaypointsForCurrentDimension(Minecraft minecraft) {
        String dimensionKey = getCurrentDimensionKey(minecraft);
        return getWaypoints(minecraft).stream()
                .filter(waypoint -> dimensionKey.equals(waypoint.dimensionKey()))
                .toList();
    }

    public static void addWaypoint(Minecraft minecraft, int x, int z) {
        if (minecraft != null && minecraft.player != null) {
            addWaypointWithName(minecraft, x, (int) minecraft.player.getY(), z, null);
        } else {
            addWaypointWithName(minecraft, x, 64, z, null);
        }
    }

    public static void addWaypointWithName(Minecraft minecraft, int x, int y, int z, String customName) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        syncContext(minecraft);
        int index = nextWaypointNumber++;
        String name = customName != null ? customName : "Waypoint " + index;
        
        WAYPOINTS.add(new MinimapWaypoint(
                name,
                getCurrentDimensionKey(minecraft),
                x,
                y,
                z,
                WAYPOINT_COLORS[(index - 1) % WAYPOINT_COLORS.length]
        ));
        saveContext(minecraft);
    }

    public static void removeWaypoint(Minecraft minecraft, MinimapWaypoint waypoint) {
        if (minecraft == null) {
            return;
        }

        syncContext(minecraft);
        if (WAYPOINTS.remove(waypoint)) {
            saveContext(minecraft);
        }
    }

    public static void updateWaypointName(Minecraft minecraft, MinimapWaypoint waypoint, String newName) {
        if (minecraft == null || waypoint == null || newName == null || newName.isBlank()) {
            return;
        }

        syncContext(minecraft);
        int index = WAYPOINTS.indexOf(waypoint);
        if (index != -1) {
            WAYPOINTS.set(index, new MinimapWaypoint(
                    newName,
                    waypoint.dimensionKey(),
                    waypoint.x(),
                    waypoint.y(),
                    waypoint.z(),
                    waypoint.color()
            ));
            saveContext(minecraft);
        }
    }

    public static MinimapWaypoint findNearestWaypoint(Minecraft minecraft, int x, int z, int maxDistance) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }

        syncContext(minecraft);
        String dimensionKey = getCurrentDimensionKey(minecraft);
        int maxDistanceSq = maxDistance * maxDistance;
        return WAYPOINTS.stream()
                .filter(waypoint -> dimensionKey.equals(waypoint.dimensionKey()))
                .filter(waypoint -> distanceSq(waypoint, x, z) <= maxDistanceSq)
                .min(Comparator.comparingInt(waypoint -> distanceSq(waypoint, x, z)))
                .orElse(null);
    }

    public static String getCurrentWorldKey() {
        return currentWorldKey;
    }

    public static String getCurrentDimensionKey(Minecraft minecraft) {
        return minecraft.level == null ? "" : minecraft.level.dimension().location().toString();
    }

    private static int distanceSq(MinimapWaypoint waypoint, int x, int z) {
        int dx = waypoint.x() - x;
        int dz = waypoint.z() - z;
        return dx * dx + dz * dz;
    }

    private static void syncContext(Minecraft minecraft) {
        String worldKey = resolveWorldKey(minecraft);
        if (Objects.equals(worldKey, currentWorldKey)) {
            return;
        }

        if (worldMap != null) {
            worldMap.saveAll();
        }

        currentWorldKey = worldKey == null ? "" : worldKey;
        WAYPOINTS.clear();
        nextWaypointNumber = 1;

        if (worldKey != null) {
            loadContext(minecraft, worldKey);
            worldMap = new MinimapWorldMap(minecraft.gameDirectory.toPath().resolve("config").resolve("ashwake_core_minimap_cache"));
        } else {
            worldMap = null;
        }
    }

    private static String resolveWorldKey(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }

        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }

        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            return "server:" + serverData.ip;
        }

        return "world:unknown";
    }

    private static void loadContext(Minecraft minecraft, String worldKey) {
        Path path = storagePath(minecraft);
        if (!Files.exists(path)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject contexts = root.has("contexts") ? root.getAsJsonObject("contexts") : null;
            if (contexts == null || !contexts.has(worldKey)) {
                return;
            }

            JsonObject context = contexts.getAsJsonObject(worldKey);
            nextWaypointNumber = context.has("nextWaypointNumber") ? context.get("nextWaypointNumber").getAsInt() : 1;

            JsonArray waypoints = context.has("waypoints") ? context.getAsJsonArray("waypoints") : new JsonArray();
            for (int index = 0; index < waypoints.size(); index++) {
                JsonObject waypoint = waypoints.get(index).getAsJsonObject();
                WAYPOINTS.add(new MinimapWaypoint(
                        waypoint.get("name").getAsString(),
                        waypoint.get("dimension").getAsString(),
                        waypoint.get("x").getAsInt(),
                        waypoint.get("y").getAsInt(),
                        waypoint.get("z").getAsInt(),
                        waypoint.get("color").getAsInt()
                ));
            }
        } catch (Exception exception) {
            AshwakeCore.LOGGER.warn("Failed to load minimap waypoints from {}", path, exception);
        }
    }

    private static void saveContext(Minecraft minecraft) {
        if (currentWorldKey.isBlank()) {
            return;
        }

        Path path = storagePath(minecraft);

        try {
            Files.createDirectories(path.getParent());
            JsonObject root = Files.exists(path)
                    ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()
                    : new JsonObject();
            JsonObject contexts = root.has("contexts") ? root.getAsJsonObject("contexts") : new JsonObject();

            JsonObject context = new JsonObject();
            context.addProperty("nextWaypointNumber", nextWaypointNumber);

            JsonArray waypoints = new JsonArray();
            for (MinimapWaypoint waypoint : WAYPOINTS) {
                JsonObject waypointJson = new JsonObject();
                waypointJson.addProperty("name", waypoint.name());
                waypointJson.addProperty("dimension", waypoint.dimensionKey());
                waypointJson.addProperty("x", waypoint.x());
                waypointJson.addProperty("y", waypoint.y());
                waypointJson.addProperty("z", waypoint.z());
                waypointJson.addProperty("color", waypoint.color());
                waypoints.add(waypointJson);
            }

            context.add("waypoints", waypoints);
            contexts.add(currentWorldKey, context);
            root.add("contexts", contexts);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AshwakeCore.LOGGER.warn("Failed to save minimap waypoints to {}", path, exception);
        }
    }

    private static Path storagePath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve("ashwake_core_minimap_waypoints.json");
    }
}
