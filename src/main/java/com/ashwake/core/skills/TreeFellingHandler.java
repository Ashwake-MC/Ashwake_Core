package com.ashwake.core.skills;

import com.ashwake.core.AshwakeCore;
import com.mojang.math.Transformation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.ashwake.core.network.SkillNetworking;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class TreeFellingHandler {
    private static final int MIN_LOGS = 3;
    private static final int MIN_LEAVES = 6;
    private static final int MAX_LOGS = 1536;
    private static final int MAX_LEAVES = 4096;
    private static final int MAX_ANIMATED_LOGS = 48;
    private static final int MAX_ANIMATED_LEAVES = 16;
    private static final int MAX_ANIMATED_PIECES = 64;
    private static final int MAX_LOG_RADIUS = 24;
    private static final int MAX_LOG_HEIGHT = 160;
    private static final int MAX_LEAF_HORIZONTAL_PADDING = 16;
    private static final int MAX_LEAF_BELOW_PADDING = 16;
    private static final int MAX_LEAF_HEIGHT_PADDING = 24;
    private static final int TRUNK_ANCHOR_HEIGHT = 10;
    private static final float DISPLAY_VIEW_RANGE = 6.0F;
    private static final int TREE_HP_TIMEOUT_TICKS = 20 * 60;
    private static final int TREE_HUD_DISPLAY_TICKS = 40;
    private static final int PRE_FALL_TICKS = 1;
    private static final int FALL_TICKS = 20;
    private static final int IMPACT_HOLD_TICKS = 6;
    private static final float FALL_ANGLE_DEGREES = 90.0F;
    private static final int TREE_REMOVAL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final Map<ServerLevel, List<FallingTreeAnimation>> ACTIVE_ANIMATIONS = new IdentityHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, TreeDurabilityState>> TREE_DURABILITY = new IdentityHashMap<>();

    private TreeFellingHandler() {
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState state = event.getState();
        if (!isTreeLog(state)) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (!player.isCreative() && (tool.isEmpty() || !tool.canPerformAction(ItemAbilities.AXE_DIG))) {
            return;
        }

        TreeStructure tree = findTree(level, event.getPos());
        if (tree == null) {
            return;
        }

        BlockPos rootPos = tree.rootPos();
        TreeHitProfile hitProfile = resolveTreeHitProfile(tree);
        TreeDurabilityState durability = applyTreeHit(level, rootPos, hitProfile);
        if (!durability.isBroken()) {
            event.setCanceled(true);
            if (!player.isCreative()) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }

            playChopSound(level, rootPos, tree.rootState(), durability.remainingHits(), durability.maxHits());
            SkillNetworking.showTreeHud(
                    player,
                    tree.rootState().getBlock().getDescriptionId(),
                    durability.remainingHits(),
                    durability.maxHits(),
                    TREE_HUD_DISPLAY_TICKS
            );
            return;
        }

        clearTreeDurability(level, rootPos);
        SkillNetworking.clearTreeHud(player);
        float yaw = resolveFallYaw(player, rootPos);
        List<TreeDisplayPiece> displayPieces = createDisplayPieces(level, rootPos, tree.animatedPieces());
        if (displayPieces.size() != tree.animatedPieces().size()) {
            return;
        }

        event.setCanceled(true);
        removeTreeBlocks(level, tree.pieces());
        displayPieces.forEach(piece -> level.addFreshEntity(piece.display()));
        playStartSound(level, rootPos, tree.rootState(), tree.pieces().size());

        ItemStack toolSnapshot = tool.copy();
        boolean dropResources = !player.isCreative();
        if (dropResources) {
            tool.hurtAndBreak(resolveFinalToolDamage(tree.logCount(), hitProfile.maxHits()), player, EquipmentSlot.MAINHAND);
            SkillEvents.award(player, SkillType.WOODCUTTING, tree.logCount() * 12);
        }

        FallingTreeAnimation animation = new FallingTreeAnimation(
                level,
                player.getUUID(),
                toolSnapshot,
                dropResources,
                tree.rootState(),
                new Vec3(rootPos.getX() + 0.5D, rootPos.getY(), rootPos.getZ() + 0.5D),
                yaw,
                tree.pieces(),
                displayPieces
        );
        ACTIVE_ANIMATIONS.computeIfAbsent(level, ignored -> new ArrayList<>()).add(animation);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ACTIVE_ANIMATIONS.isEmpty()) {
            Iterator<Map.Entry<ServerLevel, List<FallingTreeAnimation>>> levelIterator = ACTIVE_ANIMATIONS.entrySet().iterator();
            while (levelIterator.hasNext()) {
                Map.Entry<ServerLevel, List<FallingTreeAnimation>> entry = levelIterator.next();
                List<FallingTreeAnimation> animations = entry.getValue();
                animations.removeIf(FallingTreeAnimation::tick);
                if (animations.isEmpty()) {
                    levelIterator.remove();
                }
            }
        }

        if (!TREE_DURABILITY.isEmpty()) {
            Iterator<Map.Entry<ServerLevel, Map<BlockPos, TreeDurabilityState>>> levelIterator = TREE_DURABILITY.entrySet().iterator();
            while (levelIterator.hasNext()) {
                Map.Entry<ServerLevel, Map<BlockPos, TreeDurabilityState>> entry = levelIterator.next();
                ServerLevel level = entry.getKey();
                long currentTick = level.getServer().getTickCount();
                entry.getValue().entrySet().removeIf(progress -> progress.getValue().isExpired(currentTick)
                        || !isTreeLog(level.getBlockState(progress.getKey())));
                if (entry.getValue().isEmpty()) {
                    levelIterator.remove();
                }
            }
        }

    }

    private static TreeStructure findTree(ServerLevel level, BlockPos cutPos) {
        Set<BlockPos> logs = collectLogs(level, cutPos);
        if (logs.size() < MIN_LOGS) {
            return null;
        }

        BlockPos rootPos = resolveRootPos(cutPos, logs);
        TreeBounds bounds = TreeBounds.from(rootPos, logs);
        Set<BlockPos> leaves = collectLeaves(level, bounds, logs);
        if (leaves.size() < MIN_LEAVES && !isLikelyLeaflessTree(logs, bounds)) {
            return null;
        }

        List<TreePiece> logPieces = logs.stream()
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> horizontalDistanceSquared(rootPos, pos))
                        .thenComparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ()))
                .map(pos -> new TreePiece(pos, level.getBlockState(pos), cornerOffset(rootPos, pos), centerOffset(rootPos, pos)))
                .toList();
        List<TreePiece> leafPieces = leaves.stream()
                .sorted(Comparator.comparingInt((BlockPos pos) -> -pos.getY())
                        .thenComparingInt(pos -> -horizontalDistanceSquared(rootPos, pos))
                        .thenComparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ()))
                .map(pos -> new TreePiece(pos, level.getBlockState(pos), cornerOffset(rootPos, pos), centerOffset(rootPos, pos)))
                .toList();

        List<TreePiece> pieces = new ArrayList<>(logPieces.size() + leafPieces.size());
        pieces.addAll(logPieces);
        pieces.addAll(leafPieces);

        return new TreeStructure(rootPos, pieces, selectAnimatedPieces(rootPos, logPieces, leafPieces), logs.size(), level.getBlockState(rootPos));
    }

    private static Set<BlockPos> collectLogs(ServerLevel level, BlockPos rootPos) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(rootPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current) || !withinLogBounds(rootPos, current)) {
                continue;
            }

            if (!isTreeLog(level.getBlockState(current))) {
                continue;
            }

            logs.add(current);
            if (logs.size() > MAX_LOGS) {
                return Set.of();
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        queue.addLast(current.offset(dx, dy, dz));
                    }
                }
            }
        }

        return logs;
    }

    private static Set<BlockPos> collectLeaves(ServerLevel level, TreeBounds bounds, Set<BlockPos> logs) {
        Set<BlockPos> seedLeaves = new HashSet<>();
        for (BlockPos logPos : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        BlockPos candidate = logPos.offset(dx, dy, dz);
                        if (withinLeafBounds(bounds, candidate) && isTreeLeaf(level.getBlockState(candidate))) {
                            seedLeaves.add(candidate);
                        }
                    }
                }
            }
        }

        if (seedLeaves.isEmpty()) {
            return Set.of();
        }

        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(seedLeaves);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current) || !withinLeafBounds(bounds, current)) {
                continue;
            }

            if (!isTreeLeaf(level.getBlockState(current))) {
                continue;
            }

            leaves.add(current);
            if (leaves.size() > MAX_LEAVES) {
                return Set.of();
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        queue.addLast(current.offset(dx, dy, dz));
                    }
                }
            }
        }

        return leaves;
    }

    private static BlockPos resolveRootPos(BlockPos cutPos, Set<BlockPos> logs) {
        return logs.stream()
                .min(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(pos -> horizontalDistanceSquared(cutPos, pos))
                        .thenComparingInt(pos -> pos.getX())
                        .thenComparingInt(pos -> pos.getZ()))
                .orElse(cutPos);
    }

    private static boolean isLikelyLeaflessTree(Set<BlockPos> logs, TreeBounds bounds) {
        int height = (bounds.maxY() - bounds.minY()) + 1;
        int spanX = (bounds.maxX() - bounds.minX()) + 1;
        int spanZ = (bounds.maxZ() - bounds.minZ()) + 1;
        long layers = logs.stream().map(BlockPos::getY).distinct().count();
        if (layers <= 0) {
            return false;
        }

        double averageLogsPerLayer = logs.size() / (double) layers;
        return logs.size() >= 6
                && height >= 4
                && spanX <= 16
                && spanZ <= 16
                && averageLogsPerLayer <= 12.0D;
    }

    private static TreeHitProfile resolveTreeHitProfile(TreeStructure tree) {
        String species = resolveTreeSpecies(tree.rootState());
        int hits = 2 + resolveRarityBonus(species) + resolveSizeBonus(tree.logCount());
        return new TreeHitProfile(species, Math.min(12, hits));
    }

    private static TreeDurabilityState applyTreeHit(ServerLevel level, BlockPos rootPos, TreeHitProfile hitProfile) {
        long currentTick = level.getServer().getTickCount();
        Map<BlockPos, TreeDurabilityState> progressByRoot = TREE_DURABILITY.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        TreeDurabilityState durability = progressByRoot.get(rootPos);
        if (durability == null || !durability.matches(hitProfile.species(), hitProfile.maxHits()) || durability.isExpired(currentTick)) {
            durability = new TreeDurabilityState(hitProfile.species(), hitProfile.maxHits(), currentTick);
            progressByRoot.put(rootPos.immutable(), durability);
        }

        durability.applyHit(currentTick);
        return durability;
    }

    private static void clearTreeDurability(ServerLevel level, BlockPos rootPos) {
        Map<BlockPos, TreeDurabilityState> progressByRoot = TREE_DURABILITY.get(level);
        if (progressByRoot == null) {
            return;
        }

        progressByRoot.remove(rootPos);
        if (progressByRoot.isEmpty()) {
            TREE_DURABILITY.remove(level);
        }
    }

    private static int resolveFinalToolDamage(int logCount, int maxHits) {
        return Math.max(1, logCount - Math.max(0, maxHits - 1));
    }

    private static int resolveRarityBonus(String species) {
        return switch (species) {
            case "oak", "birch", "spruce", "acacia", "jungle", "cherry",
                    "maple", "magnolia", "pine", "cypress", "willow", "silver_birch", "small_oak", "dead" -> 0;
            case "dark_oak", "mangrove", "ashen", "alpha", "bamboo", "eucalyptus",
                    "larch", "mauve", "palm", "apple_oak", "blue_magnolia", "orange_maple",
                    "pink_magnolia", "red_maple", "white_magnolia" -> 1;
            case "baobab", "blackwood", "joshua", "kapok", "socotra" -> 2;
            case "redwood", "cobalt", "brimwood", "blue_bioshroom", "green_bioshroom",
                    "pink_bioshroom", "yellow_bioshroom" -> 3;
            default -> 1;
        };
    }

    private static int resolveSizeBonus(int logCount) {
        if (logCount >= 160) {
            return 4;
        }
        if (logCount >= 80) {
            return 3;
        }
        if (logCount >= 36) {
            return 2;
        }
        if (logCount >= 14) {
            return 1;
        }

        return 0;
    }

    private static boolean withinLogBounds(BlockPos rootPos, BlockPos candidate) {
        return Math.abs(candidate.getX() - rootPos.getX()) <= MAX_LOG_RADIUS
                && Math.abs(candidate.getZ() - rootPos.getZ()) <= MAX_LOG_RADIUS
                && candidate.getY() >= rootPos.getY() - 8
                && candidate.getY() <= rootPos.getY() + MAX_LOG_HEIGHT;
    }

    private static boolean withinLeafBounds(TreeBounds bounds, BlockPos candidate) {
        return candidate.getX() >= bounds.minX() - MAX_LEAF_HORIZONTAL_PADDING
                && candidate.getX() <= bounds.maxX() + MAX_LEAF_HORIZONTAL_PADDING
                && candidate.getZ() >= bounds.minZ() - MAX_LEAF_HORIZONTAL_PADDING
                && candidate.getZ() <= bounds.maxZ() + MAX_LEAF_HORIZONTAL_PADDING
                && candidate.getY() >= bounds.minY() - MAX_LEAF_BELOW_PADDING
                && candidate.getY() <= bounds.maxY() + MAX_LEAF_HEIGHT_PADDING;
    }

    private static Vec3 cornerOffset(BlockPos rootPos, BlockPos pos) {
        return new Vec3(
                pos.getX() - rootPos.getX() - 0.5D,
                pos.getY() - rootPos.getY(),
                pos.getZ() - rootPos.getZ() - 0.5D
        );
    }

    private static Vec3 centerOffset(BlockPos rootPos, BlockPos pos) {
        return new Vec3(
                pos.getX() - rootPos.getX(),
                pos.getY() - rootPos.getY() + 0.5D,
                pos.getZ() - rootPos.getZ()
        );
    }

    private static List<TreeDisplayPiece> createDisplayPieces(ServerLevel level, BlockPos rootPos, List<TreePiece> pieces) {
        List<TreeDisplayPiece> displayPieces = new ArrayList<>(pieces.size());
        for (TreePiece piece : pieces) {
            Display.BlockDisplay display = createDisplay(level, rootPos, piece);
            if (display == null) {
                return List.of();
            }

            displayPieces.add(new TreeDisplayPiece(piece, display));
        }

        return displayPieces;
    }

    private static Display.BlockDisplay createDisplay(ServerLevel level, BlockPos rootPos, TreePiece piece) {
        Display.BlockDisplay display = createDisplayEntity(level);
        if (display == null) {
            return null;
        }

        try {
            DisplayInitHolder.SET_BLOCK_STATE.invoke(display, piece.state());
            DisplayInitHolder.SET_VIEW_RANGE.invoke(display, DISPLAY_VIEW_RANGE);
            DisplayInitHolder.SET_TRANSFORMATION.invoke(display, createTransformation(piece.cornerOffset(), new Quaternionf()));
        } catch (ReflectiveOperationException exception) {
            AshwakeCore.LOGGER.error("Failed to initialize block display entity for tree felling.", exception);
            return null;
        }

        display.setPos(rootPos.getX() + 0.5D, rootPos.getY(), rootPos.getZ() + 0.5D);
        display.setNoGravity(true);
        display.setYRot(0.0F);
        display.setXRot(0.0F);
        display.yRotO = 0.0F;
        display.xRotO = 0.0F;
        return display;
    }

    private static Display.BlockDisplay createDisplayEntity(ServerLevel level) {
        return new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
    }

    private static void removeTreeBlocks(ServerLevel level, List<TreePiece> pieces) {
        for (TreePiece piece : pieces) {
            BlockState state = level.getBlockState(piece.pos());
            if (state.isAir()) {
                continue;
            }

            level.setBlock(piece.pos(), state.getFluidState().createLegacyBlock(), TREE_REMOVAL_FLAGS);
        }
    }

    private static float resolveFallYaw(ServerPlayer player, BlockPos rootPos) {
        Vec3 rootCenter = new Vec3(rootPos.getX() + 0.5D, rootPos.getY(), rootPos.getZ() + 0.5D);
        Vec3 horizontal = rootCenter.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        }

        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        return (float) Math.toDegrees(Math.atan2(-horizontal.x, horizontal.z));
    }

    private static void playStartSound(ServerLevel level, BlockPos rootPos, BlockState state, int blockCount) {
        SoundType soundType = state.getSoundType();
        float volume = Math.min(1.8F, 0.8F + (blockCount * 0.015F));
        level.playSound(null, rootPos, soundType.getBreakSound(), SoundSource.BLOCKS, volume, soundType.getPitch() * 0.9F);
    }

    private static void playChopSound(ServerLevel level, BlockPos rootPos, BlockState state, int remainingHits, int maxHits) {
        SoundType soundType = state.getSoundType();
        float progress = 1.0F - (remainingHits / (float) Math.max(1, maxHits));
        level.playSound(
                null,
                rootPos,
                soundType.getHitSound(),
                SoundSource.BLOCKS,
                0.7F + (progress * 0.25F),
                soundType.getPitch() * (0.95F - (progress * 0.1F))
        );
    }

    private static Vec3 rotate(Vec3 vector, float yawDegrees, float pitchDegrees) {
        Vector3f rotated = new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
        createFallRotation(yawDegrees, pitchDegrees).transform(rotated);
        return new Vec3(rotated.x(), rotated.y(), rotated.z());
    }

    private static boolean isTreeLog(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return true;
        }

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) {
            return false;
        }

        String path = key.getPath();
        return path.endsWith("_log")
                || path.endsWith("_wood")
                || path.endsWith("_stem")
                || path.endsWith("_hyphae")
                || path.endsWith("_branch")
                || path.endsWith("_beard")
                || path.endsWith("_log_magma")
                || path.startsWith("stripped_") && (
                        path.endsWith("_log")
                                || path.endsWith("_wood")
                                || path.endsWith("_stem")
                                || path.endsWith("_hyphae")
                );
    }

    private static boolean isTreeLeaf(BlockState state) {
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) {
            return false;
        }

        String path = key.getPath();
        return path.endsWith("_leaves")
                || path.endsWith("_webbing");
    }

    private static String resolveTreeSpecies(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) {
            return "tree";
        }

        String path = key.getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }

        String[] suffixes = {"_log_magma", "_hyphae", "_branch", "_beard", "_leaves", "_wood", "_stem", "_log"};
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }

        return path;
    }

    private static float easing(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - (inverse * inverse * inverse);
    }

    private static List<TreePiece> selectAnimatedPieces(BlockPos rootPos, List<TreePiece> logPieces, List<TreePiece> leafPieces) {
        List<TreePiece> animated = new ArrayList<>(Math.min(MAX_ANIMATED_PIECES, logPieces.size() + leafPieces.size()));
        animated.addAll(selectAnimatedLogs(rootPos, logPieces));

        int leafBudget = Math.min(MAX_ANIMATED_LEAVES, Math.max(0, MAX_ANIMATED_PIECES - animated.size()));
        if (leafBudget > 0 && !leafPieces.isEmpty()) {
            animated.addAll(selectAnimatedLeaves(rootPos, leafPieces, leafBudget));
        }

        return animated;
    }

    private static List<TreePiece> selectAnimatedLogs(BlockPos rootPos, List<TreePiece> logPieces) {
        if (logPieces.size() <= MAX_ANIMATED_LOGS) {
            return logPieces;
        }

        List<TreePiece> lowerTrunk = logPieces.stream()
                .filter(piece -> piece.pos().getY() <= rootPos.getY() + TRUNK_ANCHOR_HEIGHT)
                .sorted(Comparator.comparingInt((TreePiece piece) -> horizontalDistanceSquared(rootPos, piece.pos()))
                        .thenComparingInt(piece -> piece.pos().getY())
                        .thenComparingInt(piece -> piece.pos().getX())
                        .thenComparingInt(piece -> piece.pos().getZ()))
                .toList();

        List<TreePiece> trunkCore = new ArrayList<>(logPieces);
        trunkCore.sort(Comparator.comparingInt((TreePiece piece) -> horizontalDistanceSquared(rootPos, piece.pos()))
                .thenComparingInt(piece -> piece.pos().getY())
                .thenComparingInt(piece -> piece.pos().getX())
                .thenComparingInt(piece -> piece.pos().getZ()));

        List<TreePiece> prioritized = new ArrayList<>(MAX_ANIMATED_LOGS * 2);
        prioritized.addAll(sampleEvenly(lowerTrunk, Math.min(16, MAX_ANIMATED_LOGS)));
        prioritized.addAll(sampleEvenly(logPieces, MAX_ANIMATED_LOGS));
        prioritized.addAll(sampleEvenly(trunkCore, Math.max(8, MAX_ANIMATED_LOGS / 2)));
        return limitDistinct(prioritized, MAX_ANIMATED_LOGS);
    }

    private static List<TreePiece> selectAnimatedLeaves(BlockPos rootPos, List<TreePiece> leafPieces, int limit) {
        if (leafPieces.size() <= limit) {
            return leafPieces;
        }

        List<TreePiece> canopyOutline = new ArrayList<>(leafPieces);
        canopyOutline.sort(Comparator.comparingInt((TreePiece piece) -> -piece.pos().getY())
                .thenComparingInt((TreePiece piece) -> -horizontalDistanceSquared(rootPos, piece.pos()))
                .thenComparingInt(piece -> piece.pos().getX())
                .thenComparingInt(piece -> piece.pos().getZ()));

        List<TreePiece> prioritized = new ArrayList<>(limit * 2);
        prioritized.addAll(sampleEvenly(canopyOutline, limit));
        prioritized.addAll(sampleEvenly(leafPieces, Math.max(4, limit / 2)));
        return limitDistinct(prioritized, limit);
    }

    private static int horizontalDistanceSquared(BlockPos rootPos, BlockPos pos) {
        int dx = pos.getX() - rootPos.getX();
        int dz = pos.getZ() - rootPos.getZ();
        return (dx * dx) + (dz * dz);
    }

    private static <T> List<T> limitDistinct(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        List<T> limited = new ArrayList<>(limit);
        Set<T> seen = new HashSet<>();
        for (T value : values) {
            if (!seen.add(value)) {
                continue;
            }

            limited.add(value);
            if (limited.size() >= limit) {
                break;
            }
        }

        return limited;
    }

    private static <T> List<T> sampleEvenly(List<T> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }

        List<T> sampled = new ArrayList<>(limit);
        if (limit == 1) {
            sampled.add(values.getFirst());
            return sampled;
        }

        for (int index = 0; index < limit; index++) {
            int sourceIndex = Math.round(index * (values.size() - 1) / (float) (limit - 1));
            sampled.add(values.get(sourceIndex));
        }

        return sampled;
    }

    private static Quaternionf createFallRotation(float yawDegrees, float pitchDegrees) {
        return new Quaternionf()
                .rotationYXZ((float) Math.toRadians(-yawDegrees), (float) Math.toRadians(pitchDegrees), 0.0F);
    }

    private static Transformation createTransformation(Vec3 translation, Quaternionf rotation) {
        return new Transformation(
                new Vector3f((float) translation.x, (float) translation.y, (float) translation.z),
                new Quaternionf(rotation),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf()
        );
    }

    private record TreePiece(BlockPos pos, BlockState state, Vec3 cornerOffset, Vec3 centerOffset) {
    }

    private record TreeStructure(BlockPos rootPos, List<TreePiece> pieces, List<TreePiece> animatedPieces, int logCount, BlockState rootState) {
    }

    private record TreeDisplayPiece(TreePiece piece, Display.BlockDisplay display) {
    }

    private record TreeHitProfile(String species, int maxHits) {
    }

    private record TreeBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static TreeBounds from(BlockPos rootPos, Set<BlockPos> logs) {
            int minX = rootPos.getX();
            int maxX = rootPos.getX();
            int minY = rootPos.getY();
            int maxY = rootPos.getY();
            int minZ = rootPos.getZ();
            int maxZ = rootPos.getZ();

            for (BlockPos logPos : logs) {
                minX = Math.min(minX, logPos.getX());
                maxX = Math.max(maxX, logPos.getX());
                minY = Math.min(minY, logPos.getY());
                maxY = Math.max(maxY, logPos.getY());
                minZ = Math.min(minZ, logPos.getZ());
                maxZ = Math.max(maxZ, logPos.getZ());
            }

            return new TreeBounds(minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    private static final class TreeDurabilityState {
        private final String species;
        private final int maxHits;
        private int hitsTaken;
        private long lastTouchedTick;

        private TreeDurabilityState(String species, int maxHits, long currentTick) {
            this.species = species;
            this.maxHits = maxHits;
            this.lastTouchedTick = currentTick;
        }

        private void applyHit(long currentTick) {
            this.lastTouchedTick = currentTick;
            if (this.hitsTaken < this.maxHits) {
                this.hitsTaken++;
            }
        }

        private boolean matches(String species, int maxHits) {
            return this.maxHits == maxHits && this.species.equals(species);
        }

        private boolean isBroken() {
            return this.hitsTaken >= this.maxHits;
        }

        private int remainingHits() {
            return Math.max(0, this.maxHits - this.hitsTaken);
        }

        private int maxHits() {
            return this.maxHits;
        }

        private boolean isExpired(long currentTick) {
            return currentTick - this.lastTouchedTick > TREE_HP_TIMEOUT_TICKS;
        }
    }

    private static final class DisplayInitHolder {
        private static final Method SET_BLOCK_STATE;
        private static final Method SET_TRANSFORMATION;
        private static final Method SET_TRANSFORMATION_INTERPOLATION_DELAY;
        private static final Method SET_TRANSFORMATION_INTERPOLATION_DURATION;
        private static final Method SET_VIEW_RANGE;

        static {
            try {
                SET_BLOCK_STATE = Display.BlockDisplay.class.getDeclaredMethod("setBlockState", BlockState.class);
                SET_BLOCK_STATE.setAccessible(true);
                SET_TRANSFORMATION = Display.class.getDeclaredMethod("setTransformation", Transformation.class);
                SET_TRANSFORMATION.setAccessible(true);
                SET_TRANSFORMATION_INTERPOLATION_DELAY = Display.class.getDeclaredMethod("setTransformationInterpolationDelay", int.class);
                SET_TRANSFORMATION_INTERPOLATION_DELAY.setAccessible(true);
                SET_TRANSFORMATION_INTERPOLATION_DURATION = Display.class.getDeclaredMethod("setTransformationInterpolationDuration", int.class);
                SET_TRANSFORMATION_INTERPOLATION_DURATION.setAccessible(true);
                SET_VIEW_RANGE = Display.class.getDeclaredMethod("setViewRange", float.class);
                SET_VIEW_RANGE.setAccessible(true);
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    private static final class FallingTreeAnimation {
        private final ServerLevel level;
        private final UUID playerId;
        private final ItemStack toolSnapshot;
        private final boolean dropResources;
        private final BlockState impactState;
        private final Vec3 pivot;
        private final float yaw;
        private final List<TreePiece> allPieces;
        private final List<TreeDisplayPiece> animatedPieces;
        private int tick;
        private boolean impactSoundPlayed;

        private FallingTreeAnimation(
                ServerLevel level,
                UUID playerId,
                ItemStack toolSnapshot,
                boolean dropResources,
                BlockState impactState,
                Vec3 pivot,
                float yaw,
                List<TreePiece> allPieces,
                List<TreeDisplayPiece> animatedPieces
        ) {
            this.level = level;
            this.playerId = playerId;
            this.toolSnapshot = toolSnapshot;
            this.dropResources = dropResources;
            this.impactState = impactState;
            this.pivot = pivot;
            this.yaw = yaw;
            this.allPieces = allPieces;
            this.animatedPieces = animatedPieces;
        }

        private boolean tick() {
            this.tick++;
            if (this.tick <= PRE_FALL_TICKS) {
                return false;
            }

            int animationTick = this.tick - PRE_FALL_TICKS;
            if (animationTick <= FALL_TICKS) {
                advanceFall(animationTick / (float) FALL_TICKS);
            }

            if (!this.impactSoundPlayed && animationTick >= FALL_TICKS) {
                playImpactSound();
                this.impactSoundPlayed = true;
            }

            if (animationTick >= FALL_TICKS + IMPACT_HOLD_TICKS) {
                finish();
                return true;
            }

            return false;
        }

        private void advanceFall(float progress) {
            float easedProgress = easing(Math.min(1.0F, Math.max(0.0F, progress)));
            float pitch = easedProgress * FALL_ANGLE_DEGREES;
            Quaternionf rotation = createFallRotation(this.yaw, pitch);
            for (TreeDisplayPiece piece : this.animatedPieces) {
                Display.BlockDisplay display = piece.display();
                if (display.isRemoved()) {
                    continue;
                }

                Vec3 offset = rotate(piece.piece().cornerOffset(), this.yaw, pitch);
                try {
                    DisplayInitHolder.SET_TRANSFORMATION_INTERPOLATION_DURATION.invoke(display, 1);
                    DisplayInitHolder.SET_TRANSFORMATION_INTERPOLATION_DELAY.invoke(display, 0);
                    DisplayInitHolder.SET_TRANSFORMATION.invoke(display, createTransformation(offset, rotation));
                } catch (ReflectiveOperationException exception) {
                    AshwakeCore.LOGGER.error("Failed to advance tree-felling animation.", exception);
                    display.discard();
                }
            }
        }

        private void playImpactSound() {
            SoundType soundType = this.impactState.getSoundType();
            this.level.playSound(
                    null,
                    BlockPos.containing(this.pivot.x, this.pivot.y, this.pivot.z),
                    soundType.getFallSound(),
                    SoundSource.BLOCKS,
                    Math.min(2.0F, 0.9F + (this.allPieces.size() * 0.01F)),
                    soundType.getPitch() * 0.85F
            );
        }

        private void finish() {
            ServerPlayer player = this.level.getServer().getPlayerList().getPlayer(this.playerId);
            float finalPitch = FALL_ANGLE_DEGREES;

            for (TreeDisplayPiece piece : this.animatedPieces) {
                Display.BlockDisplay display = piece.display();
                if (!display.isRemoved()) {
                    display.discard();
                }
            }

            if (!this.dropResources) {
                return;
            }

            for (TreePiece piece : this.allPieces) {
                Vec3 finalCenter = this.pivot.add(rotate(piece.centerOffset(), this.yaw, finalPitch));
                Block.dropResources(
                        piece.state(),
                        this.level,
                        BlockPos.containing(finalCenter.x, finalCenter.y, finalCenter.z),
                        null,
                        player,
                        this.toolSnapshot
                );
            }
        }
    }
}
