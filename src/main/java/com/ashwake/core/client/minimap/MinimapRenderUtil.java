package com.ashwake.core.client.minimap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.MapColor;

public final class MinimapRenderUtil {
    private static final int FULLSCREEN_MAX_SIZE = 704;
    private static final int HUD_MAX_SIZE = 88;
    private static final int HUD_MIN_SIZE = 54;
    private static final int HUD_MARGIN_RIGHT = 14;
    private static final int HUD_MARGIN_TOP = 40;
    private static final int FULLSCREEN_TOP = 52;
    private static final int FULLSCREEN_BOTTOM_RESERVE = 164;
    private static final int FULLSCREEN_BOTTOM_PADDING = 108;
    private static final int FULLSCREEN_SIDE_PADDING = 24;
    private static final int FULLSCREEN_PANEL_TOP_PADDING = 22;
    private static final int CAVE_TRIGGER_DEPTH = 8;
    private static final int CAVE_SCAN_UP = 6;
    private static final int CAVE_SCAN_DOWN = 26;
    private static final int CELL_RELIEF_THRESHOLD = 4;
    private static final int CONTOUR_STEP_SURFACE = 12;
    private static final int CONTOUR_STEP_CAVE = 8;
    private static final float HILLSHADE_XZ_SCALE = 1.15F;
    private static final float HILLSHADE_VERTICAL_SCALE = 1.85F;
    private static final float LIGHT_X = -0.45F;
    private static final float LIGHT_Y = 0.88F;
    private static final float LIGHT_Z = -0.15F;

    private static final int STRIDE = 1024;
    private static final int CACHE_SIZE = STRIDE * STRIDE;
    private static final byte FLAG_UNDERGROUND = 1;

    private static final MinimapWorldMap.SampleResult persistentSample = new MinimapWorldMap.SampleResult();
    private static final RenderCache HUD_RENDER_CACHE = new RenderCache("ashwake_core_minimap_hud");
    private static final RenderCache FULLSCREEN_RENDER_CACHE = new RenderCache("ashwake_core_minimap_fullscreen");
    private static double hudZoomIndex = -1;

    private MinimapRenderUtil() {
    }

    public static MapView createHudView(int screenWidth, int screenHeight, float zoom) {
        int baseSize = Math.min(HUD_MAX_SIZE, Math.min(screenWidth / 4, screenHeight / 3));
        int size = Mth.clamp(baseSize - (baseSize % 2), HUD_MIN_SIZE, HUD_MAX_SIZE);
        return new MapView(screenWidth - size - HUD_MARGIN_RIGHT, HUD_MARGIN_TOP, size, zoom);
    }

    public static MapView createFullscreenView(int screenWidth, int screenHeight) {
        return createFullscreenView(screenWidth, screenHeight, 1.0F);
    }

    public static MapView createFullscreenView(int screenWidth, int screenHeight, float zoom) {
        // Reserve space for the waypoints list (140) + gap (20)
        int reservedWidth = 160;
        int minSidePadding = 48;
        
        // Map available size considering both map and waypoint list for centering
        int mapAvailableWidth = screenWidth - reservedWidth - minSidePadding;
        int mapAvailableHeight = screenHeight - FULLSCREEN_BOTTOM_RESERVE;
        
        int availableSize = Math.max(48, Math.min(mapAvailableWidth, mapAvailableHeight));
        int mapSize = Math.min(FULLSCREEN_MAX_SIZE, availableSize);
        mapSize = Math.max(48, mapSize - (mapSize % 4));
        
        // Total combined width of the panels: Map panel (mapSize + 24) + gap (8) + list panel (140)
        int totalUIWidth = mapSize + 172; // 24 + 8 + 140 = 172
        
        int startX;
        if (totalUIWidth <= screenWidth - 20) {
            // Center the entire block (Map + List) together for a balanced look
            startX = (screenWidth - totalUIWidth) / 2 + 12; // +12 to offset from the panel start to the map start
        } else {
            // Fallback: If they won't fit side-by-side, prioritize centering the map on the screen
            availableSize = Math.max(48, Math.min(screenWidth - 56, mapAvailableHeight));
            mapSize = Math.min(FULLSCREEN_MAX_SIZE, availableSize);
            mapSize = Math.max(48, mapSize - (mapSize % 4));
            startX = (screenWidth - mapSize) / 2;
        }

        return new MapView(startX, FULLSCREEN_TOP, mapSize, zoom);
    }

    public static void clearCaches() {
        HUD_RENDER_CACHE.valid = false;
        FULLSCREEN_RENDER_CACHE.valid = false;
        hudZoomIndex = -1;
    }

    public static void renderHud(GuiGraphics guiGraphics, Minecraft minecraft, float partialTick) {
        if (hudZoomIndex == -1) {
            hudZoomIndex = MinimapClientState.getZoomIndex();
        }
        hudZoomIndex = Mth.lerp(0.12F * partialTick, (float) hudZoomIndex, (float) MinimapClientState.getZoomIndex());

        float actualZoom = MinimapClientState.getZoom(hudZoomIndex);
        MapView view = createHudView(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), actualZoom);
        renderMap(guiGraphics, minecraft, view, minecraft.player.getX(), minecraft.player.getZ(), false, true, partialTick);
        renderHudInfo(guiGraphics, minecraft, view);
    }

    public static void renderFullscreenFooter(GuiGraphics guiGraphics, Minecraft minecraft, MapView view) {
        Font font = minecraft.font;
        int centerX = view.x() + view.size() / 2;
        int dividerY = view.y() + view.size() + 12;
        
        // Ensure instructions don't wrap too much on small maps
        int panelWidth = view.size() + 24;
        int textWidth = Math.max(160, panelWidth - 26);
        
        guiGraphics.hLine(view.x() + 12, view.x() + view.size() - 12, dividerY, 0x22FFFFFF);

        int textY = dividerY + 10;
        Component instructions = Component.translatable("screen.ashwake_core.minimap.instructions", MinimapKeyMappings.OPEN_MINIMAP.getTranslatedKeyMessage());
        
        // Scale down if map size is very small, or use more space if available
        float scale = view.size() < 128 ? 0.75F : 1.0F;
        int lineGap = (int) (10 * scale);

        if (scale < 1.0F) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, textY, 0);
            guiGraphics.pose().scale(scale, scale, 1.0F);
            
            int scaledTextWidth = (int) (textWidth / scale);
            int currentY = 0;
            for (FormattedCharSequence line : font.split(instructions, scaledTextWidth)) {
                guiGraphics.drawCenteredString(font, line, 0, currentY, 0xFFA0A0A0);
                currentY += 10;
            }
            guiGraphics.pose().popPose();
            textY += (currentY * scale);
        } else {
            for (FormattedCharSequence line : font.split(instructions, textWidth)) {
                guiGraphics.drawCenteredString(font, line, centerX, textY, 0xFFA0A0A0);
                textY += 10;
            }
        }

        int finalTextY = textY;
        drawScaledCenteredText(guiGraphics, font, currentCoordinateText(minecraft), centerX, finalTextY + 4, textWidth, 0xFFF0E6CC, (int)(scale * 100), 50);
        drawScaledCenteredText(guiGraphics, font, currentLayerLabel(minecraft), centerX, finalTextY + 16, textWidth, 0xFF9BB1BD, (int)(scale * 100), 50);
        drawScaledCenteredText(guiGraphics, font, currentBiomeName(minecraft).getString(), centerX, finalTextY + 28, textWidth, 0xFFA8D677, (int)(scale * 100), 50);
    }

    public static boolean isInside(MapView view, double mouseX, double mouseY) {
        return mouseX >= view.x()
                && mouseX < view.x() + view.size()
                && mouseY >= view.y()
                && mouseY < view.y() + view.size();
    }

    public static BlockPos worldPosFromMouse(Minecraft minecraft, MapView view, double mouseX, double mouseY, double centerX, double centerZ) {
        float cx = view.x() + view.size() / 2.0F;
        float cy = view.y() + view.size() / 2.0F;
        
        // Simple top-down mapping (no tilt or vertical scale correction)
        double relX = mouseX - cx;
        double relY = mouseY - cy;
        
        int cells = view.cells();
        double centerCell = cells / 2.0D;
        double localX = (relX + (view.size() / 2.0D)) / view.cellSize();
        double localY = (relY + (view.size() / 2.0D)) / view.cellSize();
        
        int worldX = Mth.floor(centerX + (localX - centerCell) * view.blocksPerCell());
        int worldZ = Mth.floor(centerZ + (localY - centerCell) * view.blocksPerCell());
        int worldY = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        return new BlockPos(worldX, worldY, worldZ);
    }

    public static Component currentBiomeName(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return Component.literal("Unknown Biome");
        }

        Holder<Biome> biome = minecraft.level.getBiome(minecraft.player.blockPosition());
        return biome.unwrapKey()
                .map(ResourceKey::location)
                .map(location -> Component.translatable(location.toLanguageKey("biome")))
                .orElse(Component.literal("Unknown Biome"));
    }

    public static String currentCoordinateText(Minecraft minecraft) {
        if (minecraft.player == null) {
            return "X 0  Y 0  Z 0";
        }

        return "X " + Mth.floor(minecraft.player.getX())
                + "  Y " + Mth.floor(minecraft.player.getY())
                + "  Z " + Mth.floor(minecraft.player.getZ());
    }

    public static String currentLayerLabel(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return "Surface View";
        }

        return shouldUseUndergroundView(minecraft, minecraft.level) ? "Cave View" : "Surface View";
    }

    private static void renderHudInfo(GuiGraphics guiGraphics, Minecraft minecraft, MapView view) {
        Font font = minecraft.font;
        int centerX = view.x() + view.size() / 2;
        int textY = view.y() + view.size() + 10;
        int scale = view.size() >= 72 ? 70 : 62;
        int lineGap = view.size() >= 72 ? 9 : 8;
        int maxWidth = view.size() + 12;

        drawScaledCenteredText(guiGraphics, font, currentCoordinateText(minecraft), centerX, textY, maxWidth, 0xFFF0E6CC, scale, 48);
        drawScaledCenteredText(guiGraphics, font, currentLayerLabel(minecraft), centerX, textY + lineGap, maxWidth, 0xFF9BB1BD, scale, 46);
        drawScaledCenteredText(
                guiGraphics,
                font,
                currentBiomeName(minecraft).getString(),
                centerX,
                textY + 2 * lineGap,
                maxWidth,
                0xFFA8D677,
                scale,
                46
        );
    }

    public static void renderMap(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, boolean detailed, boolean circular, float partialTick) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        String worldKey = MinimapClientState.getCurrentWorldKey();
        String dimensionKey = MinimapClientState.getCurrentDimensionKey(minecraft);
        ClientLevel level = minecraft.level;
        boolean undergroundView = shouldUseUndergroundView(minecraft, level);
        int cells = view.cells();
        int intCenterX = Mth.floor(centerX);
        int intCenterZ = Mth.floor(centerZ);
        RenderCache cache = circular ? HUD_RENDER_CACHE : FULLSCREEN_RENDER_CACHE;

        if (circular) {
            drawCircularFrame(guiGraphics, view);
        } else {
            drawMapFrame(guiGraphics, view);
        }

        if (shouldRefreshCache(cache, view, worldKey, dimensionKey, undergroundView, intCenterX, intCenterZ)) {
            populateSamples(cache, minecraft, level, view, intCenterX, intCenterZ, worldKey, dimensionKey);
            shadeCache(cache, cache.cells, circular);
            int texSize = cache.cells + 2;
            cache.texture().setPixels(cache.pixelBuffer, texSize, texSize, STRIDE);
            cache.updateState(view, worldKey, dimensionKey, undergroundView, intCenterX, intCenterZ, MinimapClientState.getMapDataVersion(), circular);
        }

        float uWidth = view.size() / view.cellSize();
        float vWidth = view.size() / view.cellSize();

        float uOffset = (float) (centerX - cache.centerX) / view.blocksPerCell();
        float vOffset = (float) (centerZ - cache.centerZ) / view.blocksPerCell();
        
        // Use cache.cells for formula to support fractional zoom with same cache
        float centerXInCache = cache.cells / 2.0F + 1.0F;
        float u = centerXInCache + uOffset - uWidth / 2.0F;
        float v = centerXInCache + vOffset - vWidth / 2.0F;

        if (circular) {
            drawCircularBlit(guiGraphics, cache.texture().getLocation(), view.x(), view.y(), view.size(), u, v, uWidth, vWidth, STRIDE);
        } else {
            // Use float u/v with integer dimensions for blit.
            // Linear filtering on the texture handles sub-pixel smoothing during zoom.
            guiGraphics.blit(cache.texture().getLocation(), view.x(), view.y(), view.size(), view.size(), u, v, (int) Math.ceil(uWidth), (int) Math.ceil(vWidth), STRIDE, STRIDE);
        }

        renderWaypoints(guiGraphics, minecraft, view, centerX, centerZ, detailed, 0.0F, 0, circular, dimensionKey);
        renderEntities(guiGraphics, minecraft, view, centerX, centerZ, circular);
        drawPlayerMarker(guiGraphics, minecraft, view, centerX, centerZ, 0.0F, 0, circular, partialTick);

        drawCompassPoints(guiGraphics, minecraft.font, view);
    }


    private static void drawCompassPoints(GuiGraphics guiGraphics, Font font, MapView view) {
        int cx = view.x() + view.size() / 2;
        int cy = view.y() + view.size() / 2;
        int radius = view.size() / 2 + 8;

        drawScaledCenteredText(guiGraphics, font, "N", cx, cy - radius - 1, 12, 0xFFFFFFFF, 80, 60);
        drawScaledCenteredText(guiGraphics, font, "S", cx, cy + radius - 5, 12, 0xFFFFFFFF, 80, 60);
        drawScaledCenteredText(guiGraphics, font, "W", cx - radius + 2, cy - 3, 12, 0xFFFFFFFF, 80, 60);
        drawScaledCenteredText(guiGraphics, font, "E", cx + radius - 2, cy - 3, 12, 0xFFFFFFFF, 80, 60);
    }

    private static void populateSamples(RenderCache cache, Minecraft minecraft, ClientLevel level, MapView view, int centerX, int centerZ, String worldKey, String dimensionKey) {
        // Sample at least view.cells() + some margin for smooth movement/zoom without wrap-around.
        int cells = ((view.cells() + 15) / 16) * 16 + 32;
        cells = Math.min(STRIDE - 2, Math.max(128, cells));
        cache.cells = cells; // Pre-set so sampleCell knows the layout

        int sampleCount = cells + 2;
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        boolean undergroundView = shouldUseUndergroundView(minecraft, level);
        int blocksPerCell = view.blocksPerCell();
        int halfCellsPlus1 = cells / 2 + 1;

        for (int row = 0; row < sampleCount; row++) {
            int sampleZ = centerZ + (row - halfCellsPlus1) * blocksPerCell;
            int rowOffset = row * STRIDE;
            int cachedChunkX = Integer.MIN_VALUE;
            int cachedChunkZ = Integer.MIN_VALUE;
            boolean cachedChunkLoaded = false;

            for (int column = 0; column < sampleCount; column++) {
                int sampleX = centerX + (column - halfCellsPlus1) * blocksPerCell;
                int index = rowOffset + column;

                int chunkX = sampleX >> 4;
                int chunkZ = sampleZ >> 4;
                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                    cachedChunkLoaded = level.getChunkSource().hasChunk(chunkX, chunkZ);
                }

                sampleCell(cache, minecraft, level, samplePos, sampleX, sampleZ, undergroundView, blocksPerCell, index, worldKey, dimensionKey, cachedChunkLoaded);
            }
        }
    }

    private static void sampleCell(
            RenderCache cache,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            boolean undergroundView,
            int blocksPerCell,
            int index,
            String worldKey,
            String dimensionKey,
            boolean chunkLoaded
    ) {
        if (blocksPerCell <= 1) {
            // High-performance path: Try world map first to avoid expensive scans (especially underground)
            if (setPersistedOrFallback(cache, level, sampleX, sampleZ, index, worldKey, dimensionKey, true)) {
                return;
            }
            
            if (!chunkLoaded) return;
            sampleSinglePoint(cache, minecraft, level, samplePos, sampleX, sampleZ, undergroundView, index, worldKey, dimensionKey, false);
            return;
        }

        int totalRed = 0, totalGreen = 0, totalBlue = 0, totalHeight = 0;
        int undergroundCount = 0;
        int usableSamples = 0;

        // Simplified sampling for large cells to maintain performance
        // High zoom out: 2x2 samples, Medium zoom: 4x4 samples
        int step = (blocksPerCell > 32) ? blocksPerCell / 2 : Math.max(1, blocksPerCell / 4);
        
        for (int dz = 0; dz < blocksPerCell; dz += step) {
            for (int dx = 0; dx < blocksPerCell; dx += step) {
                sampleSinglePoint(cache, minecraft, level, samplePos, sampleX + dx, sampleZ + dz, undergroundView, -1, worldKey, dimensionKey, false);
                if (!isUsableMapColor(lastSampleColor)) {
                    continue;
                }
                int color = lastSampleColor;
                totalRed += (color >> 16) & 0xFF;
                totalGreen += (color >> 8) & 0xFF;
                totalBlue += color & 0xFF;
                totalHeight += lastSampleHeight;
                if (lastSampleUnderground) undergroundCount++;
                usableSamples++;
            }
        }

        if (usableSamples == 0) {
            setLastSample(cache, 0xFF0D0B0B, (short) level.getMinBuildHeight(), false, index, false);
            return;
        }

        cache.baseColors[index] = 0xFF000000
                | ((totalRed / usableSamples) << 16)
                | ((totalGreen / usableSamples) << 8)
                | (totalBlue / usableSamples);
        cache.heights[index] = (short) (totalHeight / usableSamples);
        cache.flags[index] = undergroundCount >= (usableSamples / 2) ? FLAG_UNDERGROUND : 0;
    }

    private static int lastSampleColor;
    private static short lastSampleHeight;
    private static boolean lastSampleUnderground;
    private static boolean lastSampleValid;
    private static boolean lastMapUpdateChanged;

    private static void sampleSinglePoint(
            RenderCache cache,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            boolean undergroundView,
            int index,
            String worldKey,
            String dimensionKey,
            boolean updateMap
    ) {
        lastMapUpdateChanged = false;
        int surfaceY = Math.max(level.getMinBuildHeight(), level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1);
        if (undergroundView) {
            if (sampleUnderground(cache, minecraft, level, samplePos, sampleX, sampleZ, surfaceY, index)) {
                if (updateMap) lastMapUpdateChanged = updateWorldMap(sampleX, sampleZ, worldKey, dimensionKey);
                return;
            }
        }

        if (sampleSurface(cache, minecraft, level, samplePos, sampleX, sampleZ, surfaceY, index, worldKey, dimensionKey)) {
            if (updateMap) lastMapUpdateChanged = updateWorldMap(sampleX, sampleZ, worldKey, dimensionKey);
        }
    }

    public static boolean updateWorldMap(int x, int z, String worldKey, String dimensionKey) {
        if (worldKey == null || worldKey.isBlank() || dimensionKey == null || dimensionKey.isBlank()) return false;
        if (!lastSampleValid || !isUsableMapColor(lastSampleColor)) return false;

        MinimapWorldMap map = MinimapClientState.getWorldMap();
        if (map != null) {
            return map.update(worldKey, dimensionKey, x, z, lastSampleColor, lastSampleHeight, lastSampleUnderground);
        }

        return false;
    }

    public static boolean scanChunk(Minecraft minecraft, ClientLevel level, int chunkX, int chunkZ) {
        String worldKey = MinimapClientState.getCurrentWorldKey();
        String dimensionKey = MinimapClientState.getCurrentDimensionKey(minecraft);
        BlockPos.MutableBlockPos chunkCheckPos = new BlockPos.MutableBlockPos(chunkX << 4, level.getMinBuildHeight(), chunkZ << 4);
        if (!level.hasChunkAt(chunkCheckPos)) {
            return false;
        }

        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        boolean undergroundView = shouldUseUndergroundView(minecraft, level);
        boolean changed = false;
        
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                sampleSinglePoint(HUD_RENDER_CACHE, minecraft, level, samplePos, startX + lx, startZ + lz, undergroundView, -1, worldKey, dimensionKey, true);
                changed |= lastMapUpdateChanged;
            }
        }

        return changed;
    }

    private static void setLastSample(RenderCache cache, int color, short height, boolean underground, int index) {
        setLastSample(cache, color, height, underground, index, true);
    }

    private static void setLastSample(RenderCache cache, int color, short height, boolean underground, int index, boolean valid) {
        lastSampleColor = color;
        lastSampleHeight = height;
        lastSampleUnderground = underground;
        lastSampleValid = valid;
        if (index >= 0 && index < cache.baseColors.length) {
            cache.baseColors[index] = color;
            cache.heights[index] = height;
            cache.flags[index] = underground ? FLAG_UNDERGROUND : 0;
        }
    }

    private static boolean sampleSurface(
            RenderCache cache,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            int surfaceY,
            int index,
            String worldKey,
            String dimensionKey
    ) {
        samplePos.set(sampleX, surfaceY, sampleZ);
        BlockState state = level.getBlockState(samplePos);

        int visibleY = surfaceY;
        while (isSurfacePassThrough(state) && visibleY > level.getMinBuildHeight()) {
            visibleY--;
            samplePos.set(sampleX, visibleY, sampleZ);
            state = level.getBlockState(samplePos);
        }

        if (isSurfacePassThrough(state) && state.getFluidState().isEmpty()) {
            setPersistedOrFallback(cache, level, sampleX, sampleZ, index, worldKey, dimensionKey, false);
            return false;
        }

        if (!state.getFluidState().isEmpty()) {
            FluidState fluidState = state.getFluidState();
            boolean isLava = fluidState.is(FluidTags.LAVA);
            int depth = 0;
            while (!state.getFluidState().isEmpty() && visibleY > level.getMinBuildHeight() && depth < 24) {
                visibleY--;
                samplePos.set(sampleX, visibleY, sampleZ);
                state = level.getBlockState(samplePos);
                depth++;
            }
            int floorColor = resolveBlockColor(minecraft, level, samplePos, state);
            int fluidColor;
            int alpha;
            if (isLava) {
                fluidColor = 0xFFFF4500; // Orange-red for lava
                alpha = 230; // Mostly opaque but show some hint of floor
            } else {
                fluidColor = depth < 3 ? 0xFF4AB3CC : (depth < 8 ? 0xFF2266AA : 0xFF103857);
                alpha = Math.min(235, 45 + depth * 14);
            }
            int blended = blend(floorColor, fluidColor, alpha);
            setLastSample(cache, blended, (short) (visibleY + depth), false, index, true);
            return true;
        } else {
            int resolvedColor = resolveBlockColor(minecraft, level, samplePos, state);
            if (!isUsableMapColor(resolvedColor)) {
                setPersistedOrFallback(cache, level, sampleX, sampleZ, index, worldKey, dimensionKey, false);
                return false;
            }
            setLastSample(cache, resolvedColor, (short) visibleY, false, index, true);
            return true;
        }
    }

    private static boolean sampleUnderground(
            RenderCache cache,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            int surfaceY,
            int index
    ) {
        int playerY = Mth.floor(minecraft.player.getY());
        int top = Math.min(surfaceY - 1, playerY + CAVE_SCAN_UP);
        int bottom = Math.max(level.getMinBuildHeight() + 1, playerY - CAVE_SCAN_DOWN);

        for (int y = top; y >= bottom; y--) {
            samplePos.set(sampleX, y, sampleZ);
            BlockState state = level.getBlockState(samplePos);
            if (!isCaveAir(state)) {
                continue;
            }

            samplePos.set(sampleX, y - 1, sampleZ);
            BlockState floor = level.getBlockState(samplePos);
            if (isCaveAir(floor)) {
                continue;
            }

            int depth = Math.max(0, surfaceY - y);
            int baseColor = resolveBlockColor(minecraft, level, samplePos, floor);
            int blended = blend(baseColor, 0xFF2F4150, 46 + Math.min(80, depth * 2));
            setLastSample(cache, blended, (short) (y - 1), true, index, true);
            return true;
        }

        return false;
    }

    private static int resolveBlockColor(Minecraft minecraft, ClientLevel level, BlockPos pos, BlockState state) {
        int color = minecraft.getBlockColors().getColor(state, level, pos);
        if (color == -1) {
            color = state.getMapColor(level, pos).col;
        }

        if (color == 0) {
            color = MapColor.NONE.col;
        }

        return 0xFF000000 | color;
    }

    private static int lastUndergroundCheckX = Integer.MAX_VALUE;
    private static int lastUndergroundCheckZ = Integer.MAX_VALUE;
    private static int lastUndergroundCheckY;
    private static long lastUndergroundCheckTime = -1;

    private static boolean isUnderground(Entity entity, ClientLevel level) {
        if (entity == null || level == null) return false;
        BlockPos pos = entity.blockPosition();
        
        int surfaceY;
        long gameTime = level.getGameTime();
        if (pos.getX() == lastUndergroundCheckX && pos.getZ() == lastUndergroundCheckZ && gameTime == lastUndergroundCheckTime) {
            surfaceY = lastUndergroundCheckY;
        } else {
            surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            lastUndergroundCheckX = pos.getX();
            lastUndergroundCheckZ = pos.getZ();
            lastUndergroundCheckY = surfaceY;
            lastUndergroundCheckTime = gameTime;
        }
        
        return entity.getY() + CAVE_TRIGGER_DEPTH < surfaceY && !level.canSeeSky(pos.above());
    }

    private static boolean shouldUseUndergroundView(Minecraft minecraft, ClientLevel level) {
        return isUnderground(minecraft.player, level);
    }

    private static boolean isSurfacePassThrough(BlockState state) {
        if (state.isAir()) return true;
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static boolean isCaveAir(BlockState state) {
        if (state.isAir()) return true;
        return (!state.blocksMotion() && state.getFluidState().isEmpty());
    }

    private static boolean isUsableMapColor(int color) {
        return color != 0 && color != 0xFF000000;
    }

    private static boolean setPersistedOrFallback(
            RenderCache cache,
            ClientLevel level,
            int sampleX,
            int sampleZ,
            int index,
            String worldKey,
            String dimensionKey,
            boolean persistedSamplesAreValid
    ) {
        MinimapWorldMap map = MinimapClientState.getWorldMap();
        if (map != null && map.sample(worldKey, dimensionKey, sampleX, sampleZ, persistentSample) && isUsableMapColor(persistentSample.color)) {
            setLastSample(cache, persistentSample.color, persistentSample.height, persistentSample.underground, index, persistedSamplesAreValid);
            return true;
        }

        setLastSample(cache, 0xFF0D0B0B, (short) level.getMinBuildHeight(), false, index, false);
        return false;
    }

    private static boolean shouldRefreshCache(
            RenderCache cache,
            MapView view,
            String worldKey,
            String dimensionKey,
            boolean underground,
            int centerX,
            int centerZ
    ) {
        if (!cache.valid) return true;
        if (cache.blocksPerCell != view.blocksPerCell()) return true;
        // Allow smooth panning within the pre-sampled margin before refreshing
        int marginCells = Math.max(0, (cache.cells - view.cells()) / 2 - 8);
        int maxDriftBlocks = marginCells * view.blocksPerCell();
        if (Math.abs(centerX - cache.centerX) > maxDriftBlocks) return true;
        if (Math.abs(centerZ - cache.centerZ) > maxDriftBlocks) return true;
        if (cache.underground != underground) return true;
        
        // Only refresh for data version changes if enough time has passed to avoid stuttering
        if (cache.mapDataVersion != MinimapClientState.getMapDataVersion()) {
            if (System.currentTimeMillis() - cache.lastRefreshTime > 1500) return true;
        }

        if (!Objects.equals(cache.worldKey, worldKey)) return true;
        if (!Objects.equals(cache.dimensionKey, dimensionKey)) return true;
        
        // Refresh if we need more cells or if we are too close to the edge of the current cache.
        // Adding a margin ensures we don't wrap or see old data during smooth zoom/scroll.
        if (view.cells() + 4 > cache.cells) return true;

        return false;
    }

    private static void shadeCache(RenderCache cache, int cells, boolean circular) {
        final int sampleCount = cells + 2;
        final double gradScale = 1.2D;
        final double lightX = -0.577D; 
        final double lightY = 0.577D;
        final double lightZ = -0.577D;

        // Parallel shading for large cache buffers
        IntStream.range(0, sampleCount).parallel().forEach(row -> {
            int rowStride = row * STRIDE;
            int prevRowStride = (row > 0 ? row - 1 : 0) * STRIDE;
            int nextRowStride = (row < sampleCount - 1 ? row + 1 : sampleCount - 1) * STRIDE;
            
            for (int column = 0; column < sampleCount; column++) {
                int index = rowStride + column;
                int baseColor = cache.baseColors[index];
                if (baseColor == 0) continue; 
                
                int colM1 = column > 0 ? column - 1 : 0;
                int colP1 = column < sampleCount - 1 ? column + 1 : sampleCount - 1;
                
                double nw = cache.heights[prevRowStride + colM1];
                double n  = cache.heights[prevRowStride + column];
                double ne = cache.heights[prevRowStride + colP1];
                double w  = cache.heights[rowStride + colM1];
                double e  = cache.heights[rowStride + colP1];
                double sw = cache.heights[nextRowStride + colM1];
                double s  = cache.heights[nextRowStride + column];
                double se = cache.heights[nextRowStride + colP1];

                double gradX = ((ne + 2.0 * e + se) - (nw + 2.0 * w + sw)) * 0.125; // / 8.0
                double gradZ = ((sw + 2.0 * s + se) - (nw + 2.0 * n + ne)) * 0.125;

                double nx = -gradX * gradScale;
                double nz = -gradZ * gradScale;
                double invLen = 1.0 / Math.sqrt(nx * nx + 1.0 + nz * nz);
                
                double hillshade = (nx * invLen * lightX) + (invLen * lightY) + (nz * invLen * lightZ);
                int shift = (int) ((hillshade - 0.7) * 55.0);
                if (shift < -22) shift = -22;
                else if (shift > 26) shift = 26;
                
                int r = (baseColor >> 16) & 0xFF;
                int g = (baseColor >> 8) & 0xFF;
                int b = baseColor & 0xFF;
                
                if (shift != 0) {
                    r = Math.min(255, Math.max(0, r + shift));
                    g = Math.min(255, Math.max(0, g + shift));
                    b = Math.min(255, Math.max(0, b + shift));
                }

                int color = 0xFF000000 | (r << 16) | (g << 8) | b;
                if ((cache.flags[index] & FLAG_UNDERGROUND) != 0) {
                    color = blend(color, 0xFF1C2B3A, 25);
                }
                cache.pixelBuffer[index] = color;
            }
        });
    }

    public static LivingEntity getHoveredEntity(Minecraft minecraft, MapView view, double centerX, double centerZ, double mouseX, double mouseY, boolean circular) {
        if (minecraft.level == null || minecraft.player == null) return null;

        boolean playerUnderground = shouldUseUndergroundView(minecraft, minecraft.level);
        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        double radius = view.size() / 2.0D;
        double blocksPerCell = view.blocksPerCell();
        double cellSize = view.cellSize();

        // Check entities in reverse rendering order to find the top-most one
        List<Entity> entityList = new ArrayList<>();
        minecraft.level.entitiesForRendering().forEach(entityList::add);

        for (int i = entityList.size() - 1; i >= 0; i--) {
            Entity entity = entityList.get(i);
            if (entity == minecraft.player || entity.isSpectator() || entity.isInvisibleTo(minecraft.player)) continue;
            if (!playerUnderground && isUnderground(entity, minecraft.level)) continue;

            if (entity instanceof LivingEntity living) {
                double offsetX = ((living.getX() - centerX) / blocksPerCell) * cellSize;
                double offsetY = ((living.getZ() - centerZ) / blocksPerCell) * cellSize;

                // Circular or square bounds check
                if (circular) {
                    if (offsetX * offsetX + offsetY * offsetY > (radius - 1) * (radius - 1)) continue;
                } else {
                    if (Math.abs(offsetX) > radius - 1 || Math.abs(offsetY) > radius - 1) continue;
                }

                int drawX = Mth.floor(centerPxX + offsetX);
                int drawY = Mth.floor(centerPxY + offsetY);

                int iconSize = 6;
                int half = iconSize / 2;

                if (mouseX >= drawX - half && mouseX <= drawX + half && mouseY >= drawY - half && mouseY <= drawY + half) {
                    return living;
                }
            }
        }
        return null;
    }

    private static void renderEntities(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, boolean circular) {
        if (minecraft.level == null || minecraft.player == null) return;

        boolean playerUnderground = shouldUseUndergroundView(minecraft, minecraft.level);
        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        double radius = view.size() / 2.0D;
        double blocksPerCell = view.blocksPerCell();
        double cellSize = view.cellSize();

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == minecraft.player || entity.isSpectator() || entity.isInvisibleTo(minecraft.player)) continue;
            
            if (entity instanceof LivingEntity living) {
                double offsetX = ((living.getX() - centerX) / blocksPerCell) * cellSize;
                double offsetY = ((living.getZ() - centerZ) / blocksPerCell) * cellSize;

                // Circular or square bounds check
                if (circular) {
                    if (offsetX * offsetX + offsetY * offsetY > (radius - 1) * (radius - 1)) continue;
                } else {
                    if (Math.abs(offsetX) > radius - 1 || Math.abs(offsetY) > radius - 1) continue;
                }

                // Hide cave mobs if player is on surface (checked after range for performance)
                if (!playerUnderground && isUnderground(entity, minecraft.level)) continue;

                int drawX = Mth.floor(centerPxX + offsetX);
                int drawY = Mth.floor(centerPxY + offsetY);
                renderEntityIcon(guiGraphics, minecraft, living, drawX, drawY, 6);
            }
        }
    }

    private static void renderEntityIcon(GuiGraphics guiGraphics, Minecraft minecraft, LivingEntity entity, int x, int y, int size) {
        int half = size / 2;
        
        // Background shadow (1px larger than the icon on all sides)
        guiGraphics.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, 0x99000000);
        
        if (entity instanceof AbstractClientPlayer player) {
            PlayerSkin skin = player.getSkin();
            PlayerFaceRenderer.draw(guiGraphics, skin, x - half, y - half, size);
        } else {
            ResourceLocation texture = minecraft.getEntityRenderDispatcher().getRenderer(entity).getTextureLocation(entity);
            // Heuristic: Most mobs have their face at (8, 8) in 8x8 area on a 64x64 texture
            // Scale the 8x8 face to the requested size
            guiGraphics.blit(texture, x - half, y - half, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        }
    }

    private static void renderWaypoints(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, boolean detailed, float heightScale, int refY, boolean circular, String dimensionKey) {
        if (minecraft.player == null) {
            return;
        }

        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        double edgeLimit = view.size() / 2.0D - 4.0D;

        List<MinimapWaypoint> waypoints = MinimapClientState.getWaypoints(minecraft).stream()
                .filter(waypoint -> waypoint.dimensionKey().equals(dimensionKey))
                .sorted(Comparator.comparingDouble(waypoint -> -distanceTo(waypoint, centerX, centerZ)))
                .toList();

        for (MinimapWaypoint waypoint : waypoints) {
            double offsetX = ((waypoint.x() - centerX) / view.blocksPerCell()) * view.cellSize();
            double offsetY = ((waypoint.z() - centerZ) / view.blocksPerCell()) * view.cellSize();
            
            double dist = Math.sqrt(offsetX * offsetX + offsetY * offsetY);
            double clampScale = circular ? dist / edgeLimit : Math.max(Math.abs(offsetX) / edgeLimit, Math.abs(offsetY) / edgeLimit);
            
            boolean clamped = clampScale > 1.0D;
            if (clamped) {
                offsetX /= clampScale;
                offsetY /= clampScale;
            }

            // Height parallax for waypoints (disabled for clamped/circular to keep UI clean)
            int hOffset = (clamped || circular) ? 0 : (int) ((waypoint.y() - refY) * heightScale);
            int drawX = Mth.floor(centerPxX + offsetX);
            int drawY = Mth.floor(centerPxY + offsetY) - hOffset;
            
            drawWaypointMarker(guiGraphics, drawX, drawY, waypoint.color(), clamped);

            if (detailed || clamped) {
                String label = detailed
                        ? waypoint.name() + "  " + formatDistance(distanceTo(waypoint, minecraft.player.getX(), minecraft.player.getZ()))
                        : formatDistance(distanceTo(waypoint, minecraft.player.getX(), minecraft.player.getZ()));
                int labelX = Mth.clamp(drawX + 6, view.x() + 2, view.x() + view.size() - 48);
                int labelY = Mth.clamp(drawY - 4, view.y() + 2, view.y() + view.size() - 8);
                drawScaledText(guiGraphics, minecraft.font, label, labelX, labelY, 0xFFF2E7C6, detailed ? 80 : 65);
            }
        }
    }

    private static void drawPlayerMarker(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, float heightScale, int refY, boolean circular, float partialTick) {
        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        
        double offsetX = ((minecraft.player.getX() - centerX) / view.blocksPerCell()) * view.cellSize();
        double offsetY = ((minecraft.player.getZ() - centerZ) / view.blocksPerCell()) * view.cellSize();
        
        // Bounds check
        double radius = view.size() / 2.0D;
        if (circular) {
            if (offsetX * offsetX + offsetY * offsetY > radius * radius) return;
        } else {
            if (Math.abs(offsetX) > radius || Math.abs(offsetY) > radius) return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerPxX + offsetX, centerPxY + offsetY, 20.0F);

        // Directional pointer (rotated)
        guiGraphics.pose().pushPose();
        float rotation = 180.0F + minecraft.player.getViewYRot(partialTick);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        
        // Pointer shadow
        drawTriangle(guiGraphics, 0, -11, 8, 0x99000000);
        // Pointer body
        drawTriangle(guiGraphics, 0, -10, 7, 0xFFFFFFFF);
        guiGraphics.pose().popPose();

        // Player face
        int size = 8;
        int half = size / 2;
        
        // Shadow for face
        guiGraphics.fill(-half - 1, -half - 1, half + 2, half + 2, 0x99000000);
        
        PlayerFaceRenderer.draw(guiGraphics, minecraft.player.getSkin(), -half, -half, size);
        
        // White circular border
        drawCircleOutline(guiGraphics, 0, 0, half + 1, 0xFFFFFFFF);
        
        guiGraphics.pose().popPose();
    }

    private static void drawTriangle(GuiGraphics guiGraphics, int cx, int cy, int size, int color) {
        for (int i = 0; i < size; i++) {
            guiGraphics.fill(cx - i, cy + i, cx + i + 1, cy + i + 1, color);
        }
    }

    private static void drawWaypointMarker(GuiGraphics guiGraphics, int x, int y, int color, boolean clamped) {
        int shadow = 0xCC1A120E;
        guiGraphics.fill(x - 3, y - 1, x + 4, y + 2, shadow);
        guiGraphics.fill(x - 2, y - 2, x + 3, y + 3, shadow);
        guiGraphics.fill(x - 1, y - 3, x + 2, y + 4, shadow);
        guiGraphics.fill(x - 2, y, x + 3, y + 1, color);
        guiGraphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        guiGraphics.fill(x, y - 2, x + 1, y + 3, color);

        if (clamped) {
            guiGraphics.fill(x - 4, y - 4, x + 5, y - 3, withAlpha(color, 120));
        }
    }

    public static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // Drop shadow
        guiGraphics.fill(x + 3, y + 3, x + width + 3, y + height + 3, 0x55000000);

        // Solid clean background
        guiGraphics.fill(x, y, x + width, y + height, 0xFF241C16);
        
        // Border layers
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF7D5F39); // Polished bronze
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF1A1410); // Inner gap
        guiGraphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF36281E); // Main surface

        // Outlines
        drawOutline(guiGraphics, x, y, width, height, 0xFF0D0A08);
        drawOutline(guiGraphics, x + 2, y + 2, width - 4, height - 4, 0xFF120E0B);
    }

    private static void drawCircularFrame(GuiGraphics guiGraphics, MapView view) {
        int cx = view.x() + view.size() / 2;
        int cy = view.y() + view.size() / 2;
        int radius = view.size() / 2;

        // Outer shadow
        for (int i = 0; i < 3; i++) {
            drawCircleOutline(guiGraphics, cx + 2, cy + 2, radius + 5 + i, 0x11000000);
        }

        // Main frame layers (Polished bronze/steel look)
        drawCircleOutline(guiGraphics, cx, cy, radius + 5, 0xFF1A1410); // Base dark
        drawCircleOutline(guiGraphics, cx, cy, radius + 4, 0xFF7D5F39); // Bronze rim
        drawCircleOutline(guiGraphics, cx, cy, radius + 2, 0xFF0D0A08); // Inner gap
        drawCircleOutline(guiGraphics, cx, cy, radius + 1, 0xFF36281E); // Inner thin line
    }

    private static void drawCircularBlit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int size, float u, float v, float uWidth, float vWidth, int stride) {
        float radius = size / 2.0f;
        for (int i = 0; i < size; i++) {
            float dy = (i + 0.5f) - radius;
            float dx = (float) Math.sqrt(Math.max(0, radius * radius - dy * dy));

            float xStart = radius - dx;
            float xEnd = radius + dx;
            int width = Math.round(xEnd) - Math.round(xStart);

            if (width > 0) {
                float lineU = u + (xStart / (float) size) * uWidth;
                float lineV = v + (i / (float) size) * vWidth;
                int texW = (int) Math.ceil((width / (float) size) * uWidth);
                int texH = (int) Math.ceil(vWidth / (float) size);

                guiGraphics.blit(texture, Math.round(x + xStart), y + i, width, 1, lineU, lineV, texW, texH, stride, stride);
            }
        }
    }

    private static void drawCircleOutline(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        int x = radius;
        int y = 0;
        int err = 0;

        while (x >= y) {
            drawCirclePointsOutline(guiGraphics, cx, cy, x, y, color);
            y++;
            err += 1 + 2 * y;
            if (2 * (err - x) + 1 > 0) {
                x--;
                err += 1 - 2 * x;
            }
        }
    }

    private static void drawCirclePointsOutline(GuiGraphics guiGraphics, int cx, int cy, int x, int y, int color) {
        guiGraphics.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
        guiGraphics.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
        guiGraphics.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
        guiGraphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
        guiGraphics.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
        guiGraphics.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
        guiGraphics.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
        guiGraphics.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
    }

    private static void drawMapFrame(GuiGraphics guiGraphics, MapView view) {
        int frameX = view.x() - 5;
        int frameY = view.y() - 5;
        int frameSize = view.size() + 10;

        // Outer shadow
        guiGraphics.fill(frameX + 2, frameY + 2, frameX + frameSize + 2, frameY + frameSize + 2, 0x44000000);

        // Main frame layers
        guiGraphics.fill(frameX, frameY, frameX + frameSize, frameY + frameSize, 0xFF1A1410); // Base
        guiGraphics.fill(frameX + 1, frameY + 1, frameX + frameSize - 1, frameY + frameSize - 1, 0xFF7D5F39); // Rim
        guiGraphics.fill(frameX + 2, frameY + 2, frameX + frameSize - 2, frameY + frameSize - 2, 0xFF000000); // Inner black

        // Subtle bevel
        guiGraphics.fill(frameX + 1, frameY + 1, frameX + frameSize - 1, frameY + 2, 0x44FFFFFF); // Top
        guiGraphics.fill(frameX + 1, frameY + frameSize - 2, frameX + frameSize - 1, frameY + frameSize - 1, 0x44000000); // Bottom
    }

    private static int adjustBrightness(int color, int brightnessDelta) {
        int red = Mth.clamp(((color >> 16) & 0xFF) + brightnessDelta, 0, 255);
        int green = Mth.clamp(((color >> 8) & 0xFF) + brightnessDelta, 0, 255);
        int blue = Mth.clamp((color & 0xFF) + brightnessDelta, 0, 255);
        return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private static int blend(int baseColor, int overlayColor, int alpha) {
        int clampedAlpha = Mth.clamp(alpha, 0, 255);
        int invAlpha = 255 - clampedAlpha;
        int red = ((((baseColor >> 16) & 0xFF) * invAlpha) + (((overlayColor >> 16) & 0xFF) * clampedAlpha)) / 255;
        int green = ((((baseColor >> 8) & 0xFF) * invAlpha) + (((overlayColor >> 8) & 0xFF) * clampedAlpha)) / 255;
        int blue = (((baseColor & 0xFF) * invAlpha) + ((overlayColor & 0xFF) * clampedAlpha)) / 255;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static double distanceTo(MinimapWaypoint waypoint, double x, double z) {
        double dx = waypoint.x() - x;
        double dz = waypoint.z() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String formatDistance(double distance) {
        if (distance >= 1000.0D) {
            return String.format(Locale.ROOT, "%.1fkm", distance / 1000.0D);
        }

        return Math.round(distance) + "m";
    }

    private static void drawModeBadge(GuiGraphics guiGraphics, Font font, String text, int x, int y, int fillColor) {
        int width = font.width(text) + 8;
        guiGraphics.fill(x, y, x + width, y + 10, 0xD119130F);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 9, fillColor);
        drawOutline(guiGraphics, x, y, width, 10, 0xFF12100D);
        guiGraphics.drawString(font, text, x + 4, y + 1, 0xFFF1E8D0, true);
    }

    private static void drawScaledCenteredText(
            GuiGraphics guiGraphics,
            Font font,
            String text,
            int centerX,
            int y,
            int maxWidth,
            int color,
            int preferredScalePercent,
            int minScalePercent
    ) {
        int scalePercent = preferredScalePercent;
        int naturalWidth = Math.max(1, font.width(text));
        if ((naturalWidth * scalePercent) / 100 > maxWidth) {
            scalePercent = Math.max(minScalePercent, (maxWidth * 100) / naturalWidth);
        }

        int scaledMaxWidth = Math.max(1, (maxWidth * 100) / scalePercent);
        String fittedText = font.width(text) > scaledMaxWidth ? font.plainSubstrByWidth(text, scaledMaxWidth) : text;
        int drawX = centerX - ((font.width(fittedText) * scalePercent) / 200);
        drawScaledText(guiGraphics, font, fittedText, drawX, y, color, scalePercent);
    }

    private static void drawScaledText(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, int scalePercent) {
        float scale = scalePercent / 100.0F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        
        // Draw a subtle shadow for better contrast against world background
        guiGraphics.drawString(font, text, 1, 1, 0xAA000000, false);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        
        guiGraphics.pose().popPose();
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.hLine(x, x + width - 1, y, color);
        guiGraphics.hLine(x, x + width - 1, y + height - 1, color);
        guiGraphics.vLine(x, y, y + height - 1, color);
        guiGraphics.vLine(x + width - 1, y, y + height - 1, color);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static final class RenderCache {
        private final String textureName;
        private final int[] pixelBuffer = new int[CACHE_SIZE];
        private final int[] baseColors = new int[CACHE_SIZE];
        private final short[] heights = new short[CACHE_SIZE];
        private final byte[] flags = new byte[CACHE_SIZE];

        private MinimapTexture texture;
        private boolean valid;
        private String worldKey = "";
        private String dimensionKey = "";
        private boolean underground;
        private int centerX;
        private int centerZ;
        private int cells;
        private float cellSize;
        private int blocksPerCell;
        private int size;
        private long mapDataVersion = -1L;
        private long lastRefreshTime = 0L;

        private RenderCache(String textureName) {
            this.textureName = textureName;
        }

        private MinimapTexture texture() {
            if (this.texture == null) {
                this.texture = new MinimapTexture(this.textureName, STRIDE, STRIDE);
            }
            return this.texture;
        }

        private void updateState(
                MapView view,
                String worldKey,
                String dimensionKey,
                boolean underground,
                int centerX,
                int centerZ,
                long mapDataVersion,
                boolean circular
        ) {
            this.valid = true;
            this.worldKey = worldKey;
            this.dimensionKey = dimensionKey;
            this.underground = underground;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.blocksPerCell = view.blocksPerCell();
            this.mapDataVersion = mapDataVersion;
            this.lastRefreshTime = System.currentTimeMillis();
        }
    }

    public record MapView(int x, int y, int size, float zoom) {
        public int blocksPerCell() {
            if (zoom >= 1.0F) return 1;
            if (zoom <= 0.0078125F) return 256;
            if (zoom <= 0.015625F) return 128;
            if (zoom <= 0.03125F) return 64;
            if (zoom <= 0.0625F) return 32;
            if (zoom <= 0.125F) return 16;
            if (zoom <= 0.25F) return 8;
            if (zoom <= 0.5F) return 4;
            return 2;
        }

        public float cellSize() {
            return zoom * blocksPerCell();
        }

        public int cells() {
            int needed = Mth.ceil(size / cellSize());
            return Math.min(STRIDE - 2, needed);
        }
    }
}
