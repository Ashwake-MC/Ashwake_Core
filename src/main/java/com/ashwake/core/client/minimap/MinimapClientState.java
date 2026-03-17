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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinimapClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAVE_INTERVAL_TICKS = 100;
    private static final int SCAN_RADIUS_CHUNKS = 6;
    private static final int SCAN_REFRESH_INTERVAL_TICKS = 40;
    private static final int SCAN_REFRESH_RADIUS_CHUNKS = 1;
    private static final int SCAN_CHUNK_BUDGET_PER_TICK = 12;
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
    private static int zoomIndex = 7; // Default 1.0x
    private static final float[] ZOOM_LEVELS = {0.0078125F, 0.015625F, 0.03125F, 0.0625F, 0.125F, 0.25F, 0.5F, 1.0F, 2.0F, 4.0F};

    private static MinimapTexture mapTexture;
    private static int saveCounter = 0;
    private static int scanRefreshCounter = 0;
    private static int lastScannedChunkX = Integer.MIN_VALUE;
    private static int lastScannedChunkZ = Integer.MIN_VALUE;
    private static long mapDataVersion = 0L;
    private static final ArrayDeque<Long> pendingChunkScans = new ArrayDeque<>();
    private static final Set<Long> queuedChunkScans = new HashSet<>();

    private MinimapClientState() {
    }

    public static MinimapTexture getMapTexture() {
        if (mapTexture == null) {
            mapTexture = new MinimapTexture("ashwake_core_minimap", 1024, 1024);
        }
        return mapTexture;
    }

    public static long getMapDataVersion() {
        return mapDataVersion;
    }

    public static void tick(Minecraft minecraft) {
        syncContext(minecraft);
        if (worldMap != null && ++saveCounter >= SAVE_INTERVAL_TICKS) {
            AshwakeCore.LOGGER.info("Periodic minimap save for world: {}", currentWorldKey);
            worldMap.saveAll();
            saveCounter = 0;
        }

        if (minecraft == null || minecraft.player == null || minecraft.level == null || worldMap == null) {
            return;
        }

        int currentChunkX = (int) minecraft.player.getX() >> 4;
        int currentChunkZ = (int) minecraft.player.getZ() >> 4;
        boolean movedChunks = currentChunkX != lastScannedChunkX || currentChunkZ != lastScannedChunkZ;

        if (movedChunks) {
            enqueueScanWindow(currentChunkX, currentChunkZ, lastScannedChunkX, lastScannedChunkZ);
            lastScannedChunkX = currentChunkX;
            lastScannedChunkZ = currentChunkZ;
            scanRefreshCounter = 0;
        } else if (++scanRefreshCounter >= SCAN_REFRESH_INTERVAL_TICKS) {
            enqueueRefreshArea(currentChunkX, currentChunkZ);
            scanRefreshCounter = 0;
        }

        processQueuedScans(minecraft);
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

    public static int getZoomIndex() {
        return zoomIndex;
    }

    public static float getZoom(double index) {
        int i = (int) index;
        if (i < 0) return ZOOM_LEVELS[0];
        if (i >= ZOOM_LEVELS.length - 1) return ZOOM_LEVELS[ZOOM_LEVELS.length - 1];
        
        float f = (float) (index - i);
        return Mth.lerp(f, ZOOM_LEVELS[i], ZOOM_LEVELS[i + 1]);
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
        if (worldKey == null) {
            if (!currentWorldKey.isEmpty()) {
                AshwakeCore.LOGGER.info("Leaving minimap context: {}", currentWorldKey);
                if (worldMap != null) worldMap.saveAll();
                currentWorldKey = "";
                worldMap = null;
                WAYPOINTS.clear();
                nextWaypointNumber = 1;
                MinimapRenderUtil.clearCaches();
            }
            return;
        }

        if (worldKey.equals(currentWorldKey)) {
            return;
        }

        if (worldMap != null) {
            AshwakeCore.LOGGER.info("Saving minimap for world: {}", currentWorldKey);
            worldMap.saveAll();
        }

        currentWorldKey = worldKey;
        WAYPOINTS.clear();
        nextWaypointNumber = 1;

        AshwakeCore.LOGGER.info("Loading minimap for world: {}", currentWorldKey);
        loadContext(minecraft, worldKey);
        MinimapRenderUtil.clearCaches();
        worldMap = new MinimapWorldMap(minecraft.gameDirectory.toPath().resolve("config").resolve("ashwake_core_minimap_cache"));
        saveCounter = 0;
        scanRefreshCounter = SCAN_REFRESH_INTERVAL_TICKS;
        lastScannedChunkX = Integer.MIN_VALUE;
        lastScannedChunkZ = Integer.MIN_VALUE;
        mapDataVersion = 0L;
        clearScanQueue();
    }

    private static String resolveWorldKey(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return null;
        }

        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            var server = minecraft.getSingleplayerServer();
            String levelName = server.getWorldData().getLevelName();
            if (levelName == null || levelName.isBlank()) levelName = "unnamed_world";
            
            String uniqueId = "";
            try {
                // Fallback to seed for uniqueness if folder name is not accessible.
                // In 1.20.1 it's in worldGenOptions.
                uniqueId = Long.toHexString(server.getWorldData().worldGenOptions().seed());
            } catch (Throwable t) {
                uniqueId = "unknown";
            }
            
            return "singleplayer:" + levelName + ":" + uniqueId;
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

    private static void enqueueScanWindow(int chunkX, int chunkZ, int previousChunkX, int previousChunkZ) {
        if (previousChunkX == Integer.MIN_VALUE || previousChunkZ == Integer.MIN_VALUE) {
            enqueueFullScan(chunkX, chunkZ);
            return;
        }

        int deltaX = chunkX - previousChunkX;
        int deltaZ = chunkZ - previousChunkZ;
        if (Math.abs(deltaX) > 1 || Math.abs(deltaZ) > 1) {
            clearScanQueue();
            enqueueFullScan(chunkX, chunkZ);
            return;
        }

        enqueueChunk(chunkX, chunkZ, true);

        if (deltaX != 0) {
            int edgeChunkX = chunkX + (Integer.signum(deltaX) * SCAN_RADIUS_CHUNKS);
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                enqueueChunk(edgeChunkX, chunkZ + dz, false);
            }
        }

        if (deltaZ != 0) {
            int edgeChunkZ = chunkZ + (Integer.signum(deltaZ) * SCAN_RADIUS_CHUNKS);
            for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
                enqueueChunk(chunkX + dx, edgeChunkZ, false);
            }
        }
    }

    private static void enqueueFullScan(int chunkX, int chunkZ) {
        for (int radius = 0; radius <= SCAN_RADIUS_CHUNKS; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                enqueueChunk(chunkX - radius, chunkZ + dz, radius <= 1);
                enqueueChunk(chunkX + radius, chunkZ + dz, radius <= 1);
            }
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                enqueueChunk(chunkX + dx, chunkZ - radius, radius <= 1);
                enqueueChunk(chunkX + dx, chunkZ + radius, radius <= 1);
            }
        }
    }

    private static void enqueueRefreshArea(int chunkX, int chunkZ) {
        for (int dz = -SCAN_REFRESH_RADIUS_CHUNKS; dz <= SCAN_REFRESH_RADIUS_CHUNKS; dz++) {
            for (int dx = -SCAN_REFRESH_RADIUS_CHUNKS; dx <= SCAN_REFRESH_RADIUS_CHUNKS; dx++) {
                enqueueChunk(chunkX + dx, chunkZ + dz, true);
            }
        }
    }

    private static void processQueuedScans(Minecraft minecraft) {
        boolean changed = false;
        int processed = 0;

        while (processed < SCAN_CHUNK_BUDGET_PER_TICK && !pendingChunkScans.isEmpty()) {
            long packedChunk = pendingChunkScans.removeFirst();
            queuedChunkScans.remove(packedChunk);

            int chunkX = unpackChunkX(packedChunk);
            int chunkZ = unpackChunkZ(packedChunk);
            if (MinimapRenderUtil.scanChunk(minecraft, minecraft.level, chunkX, chunkZ)) {
                changed = true;
            }
            processed++;
        }

        if (changed) {
            mapDataVersion++;
        }
    }

    private static void clearScanQueue() {
        pendingChunkScans.clear();
        queuedChunkScans.clear();
    }

    private static void enqueueChunk(int chunkX, int chunkZ, boolean priority) {
        long packedChunk = packChunkPos(chunkX, chunkZ);
        if (!queuedChunkScans.add(packedChunk)) {
            return;
        }

        if (priority) {
            pendingChunkScans.addFirst(packedChunk);
        } else {
            pendingChunkScans.addLast(packedChunk);
        }
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packedChunk) {
        return (int) (packedChunk >> 32);
    }

    private static int unpackChunkZ(long packedChunk) {
        return (int) packedChunk;
    }
}
