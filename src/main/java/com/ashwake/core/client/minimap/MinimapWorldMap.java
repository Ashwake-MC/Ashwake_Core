package com.ashwake.core.client.minimap;

import com.ashwake.core.AshwakeCore;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MinimapWorldMap {
    private static final int REGION_SIZE = 512;
    private static final int REGION_SHIFT = 9; // 2^9 = 512
    private static final int REGION_MASK = REGION_SIZE - 1;

    private final Path cacheDir;
    private final Map<RegionPos, RegionData> regionCache = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RegionPos, RegionData> eldest) {
            if (size() > 40) {
                if (eldest.getValue().dirty) {
                    eldest.getValue().save(cacheDir, eldest.getKey());
                }
                return true;
            }
            return false;
        }
    };

    public MinimapWorldMap(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            AshwakeCore.LOGGER.error("Failed to create minimap cache directory", e);
        }
    }

    public void update(String worldKey, String dimension, int x, int z, int color, short height, boolean underground) {
        if (worldKey.isBlank() || dimension.isBlank()) return;
        
        RegionPos pos = new RegionPos(x >> REGION_SHIFT, z >> REGION_SHIFT, dimension, worldKey);
        RegionData data = getOrCreateRegion(pos);
        
        int lx = x & REGION_MASK;
        int lz = z & REGION_MASK;
        int index = lz * REGION_SIZE + lx;
        
        if (data.colors[index] != color || data.heights[index] != height || ((data.flags[index] & 1) != 0) != underground) {
            data.colors[index] = color;
            data.heights[index] = height;
            data.flags[index] = (byte) (underground ? 1 : 0);
            data.dirty = true;
        }
    }

    public boolean sample(String worldKey, String dimension, int x, int z, SampleResult result) {
        if (worldKey.isBlank() || dimension.isBlank()) return false;
        
        RegionPos pos = new RegionPos(x >> REGION_SHIFT, z >> REGION_SHIFT, dimension, worldKey);
        RegionData data = getRegion(pos);
        if (data == null) return false;
        
        int lx = x & REGION_MASK;
        int lz = z & REGION_MASK;
        int index = lz * REGION_SIZE + lx;
        
        if (data.colors[index] == 0) return false;
        
        result.color = data.colors[index];
        result.height = data.heights[index];
        result.underground = (data.flags[index] & 1) != 0;
        return true;
    }

    private RegionData getRegion(RegionPos pos) {
        if (regionCache.containsKey(pos)) {
            return regionCache.get(pos);
        }
        RegionData data = RegionData.load(cacheDir, pos);
        if (data != null) {
            regionCache.put(pos, data);
        }
        return data;
    }

    private RegionData getOrCreateRegion(RegionPos pos) {
        RegionData data = getRegion(pos);
        if (data == null) {
            data = new RegionData();
            regionCache.put(pos, data);
        }
        return data;
    }

    public void saveAll() {
        for (Map.Entry<RegionPos, RegionData> entry : regionCache.entrySet()) {
            if (entry.getValue().dirty) {
                entry.getValue().save(cacheDir, entry.getKey());
            }
        }
    }

    public static class SampleResult {
        public int color;
        public short height;
        public boolean underground;
    }

    private record RegionPos(int rx, int rz, String dimension, String worldKey) {
        String getFileName() {
            String safeWorld = worldKey.replaceAll("[^a-zA-Z0-9_-]", "_");
            String safeDim = dimension.replaceAll("[^a-zA-Z0-9_-]", "_");
            return "map_" + safeWorld + "_" + safeDim + "_r." + rx + "." + rz + ".dat";
        }
    }

    private static class RegionData {
        final int[] colors = new int[REGION_SIZE * REGION_SIZE];
        final short[] heights = new short[REGION_SIZE * REGION_SIZE];
        final byte[] flags = new byte[REGION_SIZE * REGION_SIZE];
        boolean dirty = false;

        void save(Path dir, RegionPos pos) {
            Path file = dir.resolve(pos.getFileName());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
                out.writeInt(0x4D415053); // 'MAPS'
                out.writeInt(1); // version
                for (int c : colors) out.writeInt(c);
                for (short h : heights) out.writeShort(h);
                out.write(flags);
                dirty = false;
            } catch (IOException e) {
                AshwakeCore.LOGGER.error("Failed to save region " + pos, e);
            }
        }

        static RegionData load(Path dir, RegionPos pos) {
            Path file = dir.resolve(pos.getFileName());
            if (!Files.exists(file)) return null;
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
                if (in.readInt() != 0x4D415053) return null;
                if (in.readInt() != 1) return null;
                RegionData data = new RegionData();
                for (int i = 0; i < data.colors.length; i++) data.colors[i] = in.readInt();
                for (int i = 0; i < data.heights.length; i++) data.heights[i] = in.readShort();
                in.readFully(data.flags);
                return data;
            } catch (IOException e) {
                AshwakeCore.LOGGER.error("Failed to load region " + pos, e);
                return null;
            }
        }
    }
}
