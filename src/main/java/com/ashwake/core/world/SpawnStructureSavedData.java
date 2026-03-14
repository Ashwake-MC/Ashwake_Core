package com.ashwake.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

public final class SpawnStructureSavedData extends SavedData {
    private static final String DATA_NAME = "ashwake_core_spawn_structure";
    private static final String PLACED_TAG = "placed";
    private static final String ORIGIN_TAG = "origin";
    private static final String SPAWN_TAG = "spawn";
    private static final String PROTECTION_BOUNDS_TAG = "protection_bounds";
    private static final Factory<SpawnStructureSavedData> FACTORY = new Factory<>(
            SpawnStructureSavedData::new,
            SpawnStructureSavedData::load
    );

    private boolean placed;
    private BlockPos origin = BlockPos.ZERO;
    private BlockPos spawnPos = BlockPos.ZERO;
    private BoundingBox protectionBounds = new BoundingBox(BlockPos.ZERO);

    public static SpawnStructureSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static SpawnStructureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpawnStructureSavedData data = new SpawnStructureSavedData();
        data.placed = tag.getBoolean(PLACED_TAG);
        data.origin = readPos(tag, ORIGIN_TAG);
        data.spawnPos = readPos(tag, SPAWN_TAG);
        data.protectionBounds = readBounds(tag);
        return data;
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        int[] rawPos = tag.getIntArray(key);
        if (rawPos.length != 3) {
            return BlockPos.ZERO;
        }

        return new BlockPos(rawPos[0], rawPos[1], rawPos[2]);
    }

    private static BoundingBox readBounds(CompoundTag tag) {
        int[] rawBounds = tag.getIntArray(PROTECTION_BOUNDS_TAG);
        if (rawBounds.length != 6) {
            return new BoundingBox(BlockPos.ZERO);
        }

        return new BoundingBox(
                rawBounds[0],
                rawBounds[1],
                rawBounds[2],
                rawBounds[3],
                rawBounds[4],
                rawBounds[5]
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(PLACED_TAG, this.placed);

        if (this.placed) {
            tag.putIntArray(ORIGIN_TAG, new int[]{this.origin.getX(), this.origin.getY(), this.origin.getZ()});
            tag.putIntArray(SPAWN_TAG, new int[]{this.spawnPos.getX(), this.spawnPos.getY(), this.spawnPos.getZ()});
            tag.putIntArray(PROTECTION_BOUNDS_TAG, new int[]{
                    this.protectionBounds.minX(),
                    this.protectionBounds.minY(),
                    this.protectionBounds.minZ(),
                    this.protectionBounds.maxX(),
                    this.protectionBounds.maxY(),
                    this.protectionBounds.maxZ()
            });
        }

        return tag;
    }

    public boolean isPlaced() {
        return this.placed;
    }

    public BlockPos getSpawnPos() {
        return this.spawnPos;
    }

    public BlockPos getOrigin() {
        return this.origin;
    }

    public BoundingBox getProtectionBounds() {
        return this.protectionBounds;
    }

    public boolean hasProtectionBounds() {
        return !(this.protectionBounds.minX() == 0 && this.protectionBounds.minY() == 0
                && this.protectionBounds.minZ() == 0 && this.protectionBounds.maxX() == 0
                && this.protectionBounds.maxY() == 0 && this.protectionBounds.maxZ() == 0);
    }

    public boolean isProtected(BlockPos pos) {
        return this.placed && this.protectionBounds.isInside(pos);
    }

    public void ensureProtectionBounds(BoundingBox protectionBounds) {
        if (this.placed && !this.hasProtectionBounds()) {
            this.protectionBounds = protectionBounds;
            this.setDirty();
        }
    }

    public void markPlaced(BlockPos origin, BlockPos spawnPos, BoundingBox protectionBounds) {
        this.placed = true;
        this.origin = origin.immutable();
        this.spawnPos = spawnPos.immutable();
        this.protectionBounds = protectionBounds;
        this.setDirty();
    }
}
