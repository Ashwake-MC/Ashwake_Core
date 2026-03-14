package com.ashwake.core.world;

import com.ashwake.core.AshwakeCore;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class SpawnStructureHandler {
    private static final String INITIAL_SPAWN_APPLIED_TAG = "ashwake_core.initial_spawn_applied";
    private static final int PLACEMENT_SEARCH_RADIUS = 96;
    private static final int PLACEMENT_SEARCH_STEP = 4;
    private static final ResourceLocation SPAWN_TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(
            AshwakeCore.MOD_ID,
            "spawn"
    );

    private SpawnStructureHandler() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }

        SpawnStructureSavedData savedData = SpawnStructureSavedData.get(serverLevel);
        if (savedData.isPlaced()) {
            savedData.ensureProtectionBounds(resolveProtectionBounds(serverLevel, savedData));
            ensureExactSpawnRules(serverLevel);
            if (!savedData.getSpawnPos().equals(serverLevel.getSharedSpawnPos())) {
                serverLevel.setDefaultSpawnPos(savedData.getSpawnPos(), 0.0F);
            }
            return;
        }

        placeSpawnStructure(serverLevel, savedData);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.getPersistentData().getBoolean(INITIAL_SPAWN_APPLIED_TAG)) {
            return;
        }

        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        SpawnStructureSavedData savedData = SpawnStructureSavedData.get(overworld);
        if (!savedData.isPlaced()) {
            return;
        }

        BlockPos spawnPos = savedData.getSpawnPos();
        ensureExactSpawnRules(overworld);
        overworld.setDefaultSpawnPos(spawnPos, 0.0F);

        player.teleportTo(
                overworld,
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        player.getPersistentData().putBoolean(INITIAL_SPAWN_APPLIED_TAG, true);
    }

    private static void placeSpawnStructure(ServerLevel serverLevel, SpawnStructureSavedData savedData) {
        Optional<StructureTemplate> templateOptional = serverLevel.getStructureManager().get(SPAWN_TEMPLATE_ID);
        if (templateOptional.isEmpty()) {
            AshwakeCore.LOGGER.error("Missing spawn structure template {}.", SPAWN_TEMPLATE_ID);
            return;
        }

        StructureTemplate template = templateOptional.get();
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            AshwakeCore.LOGGER.error("Spawn structure template {} has invalid size {}.", SPAWN_TEMPLATE_ID, size);
            return;
        }

        BlockPos vanillaSpawn = serverLevel.getSharedSpawnPos();
        BlockPos placementCenter = findPlacementCenter(serverLevel, vanillaSpawn);
        int minX = placementCenter.getX() - (size.getX() / 2);
        int minZ = placementCenter.getZ() - (size.getZ() / 2);
        int maxX = minX + size.getX() - 1;
        int maxZ = minZ + size.getZ() - 1;

        ensureChunksLoaded(serverLevel, minX, minZ, maxX, maxZ);

        int originY = findPlacementSurface(serverLevel, placementCenter, minX, minZ, maxX, maxZ);
        BlockPos origin = new BlockPos(minX, originY, minZ);

        StructurePlaceSettings placement = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true)
                .setKnownShape(true);
        BoundingBox boundingBox = template.getBoundingBox(placement, origin);
        placement.setBoundingBox(boundingBox);

        ensureChunksLoaded(serverLevel, boundingBox.minX(), boundingBox.minZ(), boundingBox.maxX(), boundingBox.maxZ());
        clearPlacementArea(serverLevel, boundingBox);

        boolean placed = template.placeInWorld(
                serverLevel,
                origin,
                origin,
                placement,
                RandomSource.create(serverLevel.getSeed()),
                Block.UPDATE_ALL
        );
        if (!placed) {
            AshwakeCore.LOGGER.error("Failed to place spawn structure template {}.", SPAWN_TEMPLATE_ID);
            return;
        }

        int foundationMinY = buildFoundation(serverLevel, boundingBox);
        BoundingBox protectionBounds = new BoundingBox(
                boundingBox.minX(),
                foundationMinY,
                boundingBox.minZ(),
                boundingBox.maxX(),
                boundingBox.maxY(),
                boundingBox.maxZ()
        );

        BlockPos spawnPos = findSpawnPosition(serverLevel, origin, size, boundingBox);
        ensureExactSpawnRules(serverLevel);
        serverLevel.setDefaultSpawnPos(spawnPos, 0.0F);
        savedData.markPlaced(origin, spawnPos, protectionBounds);

        AshwakeCore.LOGGER.info(
                "Placed spawn structure {} near {} at {} and set world spawn to {}.",
                SPAWN_TEMPLATE_ID,
                placementCenter,
                origin,
                spawnPos
        );
    }

    private static BoundingBox resolveProtectionBounds(ServerLevel serverLevel, SpawnStructureSavedData savedData) {
        Optional<StructureTemplate> templateOptional = serverLevel.getStructureManager().get(SPAWN_TEMPLATE_ID);
        if (templateOptional.isEmpty()) {
            return savedData.getProtectionBounds();
        }

        StructureTemplate template = templateOptional.get();
        StructurePlaceSettings placement = new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true)
                .setKnownShape(true);
        BoundingBox structureBounds = template.getBoundingBox(placement, savedData.getOrigin());
        int minY = savedData.hasProtectionBounds()
                ? Math.min(savedData.getProtectionBounds().minY(), structureBounds.minY())
                : structureBounds.minY();
        return new BoundingBox(
                structureBounds.minX(),
                minY,
                structureBounds.minZ(),
                structureBounds.maxX(),
                structureBounds.maxY(),
                structureBounds.maxZ()
        );
    }

    private static void ensureExactSpawnRules(ServerLevel serverLevel) {
        serverLevel.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, serverLevel.getServer());
    }

    private static void ensureChunksLoaded(ServerLevel serverLevel, int minX, int minZ, int maxX, int maxZ) {
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                serverLevel.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static BlockPos findPlacementCenter(ServerLevel serverLevel, BlockPos vanillaSpawn) {
        BlockPos bestCandidate = vanillaSpawn;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -PLACEMENT_SEARCH_RADIUS; dx <= PLACEMENT_SEARCH_RADIUS; dx += PLACEMENT_SEARCH_STEP) {
            for (int dz = -PLACEMENT_SEARCH_RADIUS; dz <= PLACEMENT_SEARCH_RADIUS; dz += PLACEMENT_SEARCH_STEP) {
                BlockPos candidate = vanillaSpawn.offset(dx, 0, dz);
                int score = scorePlacementCenter(serverLevel, vanillaSpawn, candidate);
                if (score < bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
    }

    private static int scorePlacementCenter(ServerLevel serverLevel, BlockPos vanillaSpawn, BlockPos candidate) {
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int fluidPenalty = 0;
        int unstablePenalty = 0;

        for (int x = candidate.getX() - 2; x <= candidate.getX() + 2; x++) {
            for (int z = candidate.getZ() - 2; z <= candidate.getZ() + 2; z++) {
                int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feetPos = new BlockPos(x, surfaceY, z);
                BlockPos floorPos = feetPos.below();

                minHeight = Math.min(minHeight, surfaceY);
                maxHeight = Math.max(maxHeight, surfaceY);

                if (!serverLevel.getFluidState(feetPos).isEmpty() || !serverLevel.getFluidState(floorPos).isEmpty()) {
                    fluidPenalty += 200;
                }

                if (serverLevel.getBlockState(floorPos).getCollisionShape(serverLevel, floorPos).isEmpty()) {
                    unstablePenalty += 100;
                }
            }
        }

        int distancePenalty = Math.abs(candidate.getX() - vanillaSpawn.getX()) + Math.abs(candidate.getZ() - vanillaSpawn.getZ());
        int heightPenalty = (maxHeight - minHeight) * 12;
        return fluidPenalty + unstablePenalty + heightPenalty + distancePenalty;
    }

    private static int findPlacementSurface(
            ServerLevel serverLevel,
            BlockPos spawnCenter,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int sampleMinX = Math.max(minX, spawnCenter.getX() - 2);
        int sampleMaxX = Math.min(maxX, spawnCenter.getX() + 2);
        int sampleMinZ = Math.max(minZ, spawnCenter.getZ() - 2);
        int sampleMaxZ = Math.min(maxZ, spawnCenter.getZ() + 2);
        int[] sampledHeights = new int[(sampleMaxX - sampleMinX + 1) * (sampleMaxZ - sampleMinZ + 1)];
        int index = 0;

        for (int x = sampleMinX; x <= sampleMaxX; x++) {
            for (int z = sampleMinZ; z <= sampleMaxZ; z++) {
                sampledHeights[index++] = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }
        }

        Arrays.sort(sampledHeights);
        return sampledHeights[sampledHeights.length / 2];
    }

    private static void clearPlacementArea(ServerLevel serverLevel, BoundingBox boundingBox) {
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
                for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                    serverLevel.setBlock(new BlockPos(x, y, z), air, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static int buildFoundation(ServerLevel serverLevel, BoundingBox boundingBox) {
        int foundationMinY = boundingBox.minY();

        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
                BlockPos supportPos = findLowestSupportBlock(serverLevel, boundingBox, x, z);
                if (supportPos == null || serverLevel.getBlockEntity(supportPos) != null) {
                    continue;
                }

                BlockState supportState = getFoundationState(serverLevel, supportPos);
                if (supportState.getCollisionShape(serverLevel, supportPos).isEmpty()) {
                    continue;
                }

                for (int y = supportPos.getY() - 1; y >= serverLevel.getMinBuildHeight(); y--) {
                    BlockPos fillPos = new BlockPos(x, y, z);
                    BlockState existingState = serverLevel.getBlockState(fillPos);
                    if (!existingState.getCollisionShape(serverLevel, fillPos).isEmpty()
                            && serverLevel.getFluidState(fillPos).isEmpty()) {
                        break;
                    }

                    serverLevel.setBlock(fillPos, supportState, Block.UPDATE_ALL);
                    foundationMinY = Math.min(foundationMinY, y);
                }
            }
        }

        return foundationMinY;
    }

    private static BlockState getFoundationState(ServerLevel serverLevel, BlockPos supportPos) {
        BlockState state = serverLevel.getBlockState(supportPos);
        if (state.getBlock() == Blocks.GRASS_BLOCK) {
            return Blocks.DIRT.defaultBlockState();
        }

        return state;
    }

    private static BlockPos findLowestSupportBlock(ServerLevel serverLevel, BoundingBox boundingBox, int x, int z) {
        for (int y = boundingBox.minY(); y <= boundingBox.maxY(); y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!serverLevel.getBlockState(pos).getCollisionShape(serverLevel, pos).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    private static BlockPos findSpawnPosition(
            ServerLevel serverLevel,
            BlockPos origin,
            Vec3i size,
            BoundingBox boundingBox
    ) {
        int centerX = origin.getX() + (size.getX() / 2);
        int centerZ = origin.getZ() + (size.getZ() / 2);
        int maxRadius = Math.max(size.getX(), size.getZ());

        for (int radius = 0; radius <= maxRadius; radius++) {
            int searchMinX = Math.max(boundingBox.minX(), centerX - radius);
            int searchMaxX = Math.min(boundingBox.maxX(), centerX + radius);
            int searchMinZ = Math.max(boundingBox.minZ(), centerZ - radius);
            int searchMaxZ = Math.min(boundingBox.maxZ(), centerZ + radius);

            for (int x = searchMinX; x <= searchMaxX; x++) {
                for (int z = searchMinZ; z <= searchMaxZ; z++) {
                    BlockPos candidate = findStandingSpotInColumn(serverLevel, boundingBox, x, z);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        int fallbackY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centerX, centerZ) + 1;
        return new BlockPos(centerX, fallbackY, centerZ);
    }

    private static BlockPos findStandingSpotInColumn(ServerLevel serverLevel, BoundingBox boundingBox, int x, int z) {
        for (int y = boundingBox.minY() + 1; y <= boundingBox.maxY() + 1; y++) {
            BlockPos feetPos = new BlockPos(x, y, z);
            BlockPos belowPos = feetPos.below();
            BlockPos headPos = feetPos.above();

            if (hasSolidFloor(serverLevel, belowPos)
                    && isClearForSpawn(serverLevel, feetPos)
                    && isClearForSpawn(serverLevel, headPos)) {
                return feetPos;
            }
        }

        return null;
    }

    private static boolean hasSolidFloor(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        return !state.getCollisionShape(serverLevel, pos).isEmpty() && serverLevel.getFluidState(pos).isEmpty();
    }

    private static boolean isClearForSpawn(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        return state.getCollisionShape(serverLevel, pos).isEmpty() && serverLevel.getFluidState(pos).isEmpty();
    }
}
