package com.ashwake.core.client.minimap;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class MinimapRenderUtil {
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

    private static final int[] pixelBuffer = new int[512 * 512];
    private static final int[] baseColors = new int[512 * 512];
    private static final short[] heights = new short[512 * 512];
    private static final byte[] flags = new byte[512 * 512];
    private static final byte FLAG_UNDERGROUND = 1;

    private static final MinimapWorldMap.SampleResult persistentSample = new MinimapWorldMap.SampleResult();
    private static String currentWorldKey;
    private static String currentDimensionKey;

    private MinimapRenderUtil() {
    }

    public static MapView createHudView(int screenWidth, int screenHeight) {
        int baseSize = Math.min(HUD_MAX_SIZE, Math.min(screenWidth / 4, screenHeight / 3));
        int size = Mth.clamp(baseSize - (baseSize % 2), HUD_MIN_SIZE, HUD_MAX_SIZE);
        return new MapView(screenWidth - size - HUD_MARGIN_RIGHT, HUD_MARGIN_TOP, size, MinimapClientState.getCellSize(), MinimapClientState.getBlocksPerCell());
    }

    public static MapView createFullscreenView(int screenWidth, int screenHeight) {
        int availableSize = Math.max(48, Math.min(screenWidth - 56, screenHeight - FULLSCREEN_BOTTOM_RESERVE));
        int mapSize = Math.min(288, availableSize);
        mapSize = Math.max(48, mapSize - (mapSize % 4));
        return new MapView((screenWidth - mapSize) / 2, FULLSCREEN_TOP, mapSize, MinimapClientState.getCellSize(), MinimapClientState.getBlocksPerCell());
    }

    public static void renderHud(GuiGraphics guiGraphics, Minecraft minecraft) {
        MapView view = createHudView(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        int infoHeight = view.size() >= 72 ? 50 : 44;
        drawPanel(guiGraphics, view.x() - 7, view.y() - 8, view.size() + 14, view.size() + infoHeight);
        renderMap(guiGraphics, minecraft, view, minecraft.player.getX(), minecraft.player.getZ(), false, true);
        renderHudInfo(guiGraphics, minecraft, view);
    }

    public static void renderFullscreenFooter(GuiGraphics guiGraphics, Minecraft minecraft, MapView view) {
        Font font = minecraft.font;
        int centerX = view.x() + view.size() / 2;
        int dividerY = view.y() + view.size() + 12;
        int textWidth = view.size() + FULLSCREEN_SIDE_PADDING - 26;

        guiGraphics.hLine(view.x() + 12, view.x() + view.size() - 12, dividerY, 0x22FFFFFF);

        int textY = dividerY + 10;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.ashwake_core.minimap.instructions"), textWidth)) {
            guiGraphics.drawCenteredString(font, line, centerX, textY, 0xFFA0A0A0);
            textY += 10;
        }

        guiGraphics.drawCenteredString(font, Component.literal(currentCoordinateText(minecraft)), centerX, textY + 4, 0xFFF0E6CC);
        guiGraphics.drawCenteredString(font, Component.literal(currentLayerLabel(minecraft)), centerX, textY + 16, 0xFF9BB1BD);
        guiGraphics.drawCenteredString(font, currentBiomeName(minecraft), centerX, textY + 28, 0xFFA8D677);
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
        int textY = view.y() + view.size() + 12;
        int scale = view.size() >= 72 ? 70 : 62;
        int lineGap = view.size() >= 72 ? 9 : 8;
        int maxWidth = view.size() + 6;
        
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

    public static void renderMap(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, boolean detailed, boolean circular) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (circular) {
            drawCircularFrame(guiGraphics, view);
        } else {
            drawMapFrame(guiGraphics, view);
        }

        ClientLevel level = minecraft.level;
        int cells = view.cells();
        int intCenterX = Mth.floor(centerX);
        int intCenterZ = Mth.floor(centerZ);
        
        // Optimized sampling into persistent arrays
        populateSamples(minecraft, level, view, intCenterX, intCenterZ);

        // Map Shading into pixel buffer
        double radiusSq = (cells / 2.0) * (cells / 2.0);
        for (int row = 0; row < cells; row++) {
            double dy = row - cells / 2.0 + 0.5;
            for (int column = 0; column < cells; column++) {
                int index = row * cells + column;
                if (circular) {
                    double dx = column - cells / 2.0 + 0.5;
                    if (dx * dx + dy * dy > radiusSq) {
                        pixelBuffer[index] = 0;
                        continue;
                    }
                }
                pixelBuffer[index] = shadeSampleColor(cells, row, column);
            }
        }

        // Texture-based rendering (much faster than individual fill calls)
        MinimapTexture tex = MinimapClientState.getMapTexture();
        tex.setPixels(pixelBuffer, cells, cells);
        
        // Draw the map as one textured quad (supports scaling if cellSize > 1)
        guiGraphics.blit(tex.getLocation(), view.x(), view.y(), view.size(), view.size(), 0.0F, 0.0F, cells, cells, 512, 512);

        renderWaypoints(guiGraphics, minecraft, view, centerX, centerZ, detailed, 0.0F, 0, circular);
        drawPlayerMarker(guiGraphics, minecraft, view, centerX, centerZ, 0.0F, 0, circular);

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

    private static void populateSamples(Minecraft minecraft, ClientLevel level, MapView view, int centerX, int centerZ) {
        currentWorldKey = MinimapClientState.getCurrentWorldKey();
        currentDimensionKey = MinimapClientState.getCurrentDimensionKey(minecraft);
        
        int cells = view.cells();
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        boolean undergroundView = shouldUseUndergroundView(minecraft, level);
        int blocksPerCell = view.blocksPerCell();

        for (int row = 0; row < cells; row++) {
            int sampleZ = centerZ + ((row - cells / 2) * blocksPerCell);
            for (int column = 0; column < cells; column++) {
                int sampleX = centerX + ((column - cells / 2) * blocksPerCell);
                int index = row * cells + column;

                sampleCell(minecraft, level, samplePos, sampleX, sampleZ, undergroundView, blocksPerCell, index);
            }
        }
    }

    private static void sampleCell(
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            boolean undergroundView,
            int blocksPerCell,
            int index
    ) {
        if (blocksPerCell <= 1) {
            sampleSinglePoint(minecraft, level, samplePos, sampleX, sampleZ, undergroundView, index);
            return;
        }

        int totalRed = 0, totalGreen = 0, totalBlue = 0, totalHeight = 0;
        int undergroundCount = 0;
        int sampleCount = 0;

        // Sample a grid within the cell to ensure full world map population and sharp averaging
        // We use at most a 3x3 grid even for high blocksPerCell to maintain performance
        int step = Math.max(1, blocksPerCell / 2);
        for (int dz = 0; dz < blocksPerCell; dz += step) {
            for (int dx = 0; dx < blocksPerCell; dx += step) {
                sampleSinglePoint(minecraft, level, samplePos, sampleX + dx, sampleZ + dz, undergroundView, -1);
                int color = lastSampleColor;
                totalRed += (color >> 16) & 0xFF;
                totalGreen += (color >> 8) & 0xFF;
                totalBlue += color & 0xFF;
                totalHeight += lastSampleHeight;
                if (lastSampleUnderground) undergroundCount++;
                sampleCount++;
            }
        }

        baseColors[index] = 0xFF000000
                | ((totalRed / sampleCount) << 16)
                | ((totalGreen / sampleCount) << 8)
                | (totalBlue / sampleCount);
        heights[index] = (short) (totalHeight / sampleCount);
        flags[index] = undergroundCount >= (sampleCount / 2) ? FLAG_UNDERGROUND : 0;
    }

    private static int lastSampleColor;
    private static short lastSampleHeight;
    private static boolean lastSampleUnderground;

    private static void sampleSinglePoint(
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            boolean undergroundView,
            int index
    ) {
        samplePos.set(sampleX, level.getMinBuildHeight(), sampleZ);
        if (!level.hasChunkAt(samplePos)) {
            MinimapWorldMap map = MinimapClientState.getWorldMap();
            if (map != null && map.sample(currentWorldKey, currentDimensionKey, sampleX, sampleZ, persistentSample)) {
                setLastSample(persistentSample.color, persistentSample.height, persistentSample.underground, index);
            } else {
                setLastSample(0xFF0D0B0B, (short) level.getMinBuildHeight(), false, index);
            }
            return;
        }

        int surfaceY = Math.max(level.getMinBuildHeight(), level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1);
        if (undergroundView) {
            if (sampleUnderground(minecraft, level, samplePos, sampleX, sampleZ, surfaceY, index)) {
                updateWorldMap(sampleX, sampleZ);
                return;
            }
        }

        sampleSurface(minecraft, level, samplePos, sampleX, sampleZ, surfaceY, index);
        updateWorldMap(sampleX, sampleZ);
    }

    public static void updateWorldMap(int x, int z) {
        MinimapWorldMap map = MinimapClientState.getWorldMap();
        if (map != null) {
            map.update(currentWorldKey, currentDimensionKey, x, z, lastSampleColor, lastSampleHeight, lastSampleUnderground);
        }
    }

    public static void scanChunk(Minecraft minecraft, ClientLevel level, int chunkX, int chunkZ) {
        currentWorldKey = MinimapClientState.getCurrentWorldKey();
        currentDimensionKey = MinimapClientState.getCurrentDimensionKey(minecraft);
        
        BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
        boolean undergroundView = shouldUseUndergroundView(minecraft, level);
        
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                sampleSinglePoint(minecraft, level, samplePos, startX + lx, startZ + lz, undergroundView, -1);
            }
        }
    }

    private static void setLastSample(int color, short height, boolean underground, int index) {
        lastSampleColor = color;
        lastSampleHeight = height;
        lastSampleUnderground = underground;
        if (index >= 0 && index < baseColors.length) {
            baseColors[index] = color;
            heights[index] = height;
            flags[index] = underground ? FLAG_UNDERGROUND : 0;
        }
    }

    private static void sampleSurface(
            Minecraft minecraft,
            ClientLevel level,
            BlockPos.MutableBlockPos samplePos,
            int sampleX,
            int sampleZ,
            int surfaceY,
            int index
    ) {
        samplePos.set(sampleX, surfaceY, sampleZ);
        BlockState state = level.getBlockState(samplePos);

        int visibleY = surfaceY;
        while (isSurfacePassThrough(state) && visibleY > level.getMinBuildHeight()) {
            visibleY--;
            samplePos.set(sampleX, visibleY, sampleZ);
            state = level.getBlockState(samplePos);
        }

        setLastSample(resolveBlockColor(minecraft, level, samplePos, state), (short) visibleY, false, index);
    }

    private static boolean sampleUnderground(
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
            setLastSample(blended, (short) (y - 1), true, index);
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

    private static int shadeSampleColor(int cells, int row, int column) {
        int index = row * cells + column;
        int baseColor = baseColors[index];
        boolean underground = (flags[index] & FLAG_UNDERGROUND) != 0;
        
        double nw = heightAt(cells, row - 1, column - 1);
        double n = heightAt(cells, row - 1, column);
        double ne = heightAt(cells, row - 1, column + 1);
        double w = heightAt(cells, row, column - 1);
        double e = heightAt(cells, row, column + 1);
        double sw = heightAt(cells, row + 1, column - 1);
        double s = heightAt(cells, row + 1, column);
        double se = heightAt(cells, row + 1, column + 1);

        double gradX = ((ne + (2.0D * e) + se) - (nw + (2.0D * w) + sw)) / 8.0D;
        double gradZ = ((sw + (2.0D * s) + se) - (nw + (2.0D * n) + ne)) / 8.0D;

        double normalX = -gradX * 1.2D;
        double normalY = 1.0D;
        double normalZ = -gradZ * 1.2D;
        double normalLength = Math.sqrt((normalX * normalX) + (normalY * normalY) + (normalZ * normalZ));
        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        // Subtle NW light source for a clean relief feel
        double lightX = -0.5D;
        double lightY = 0.8D;
        double lightZ = -0.4D;
        double lightLength = Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
        lightX /= lightLength;
        lightY /= lightLength;
        lightZ /= lightLength;

        double hillshade = (normalX * lightX) + (normalY * lightY) + (normalZ * lightZ);
        int hillshadeShift = Mth.clamp((int) Math.round((hillshade - 0.75D) * 60.0D), -24, 28);
        
        int color = adjustBrightness(baseColor, hillshadeShift);
        
        if (underground) {
            color = blend(color, 0xFF1C2B3A, 30);
        }

        return color;
    }

    private static double heightAt(int cells, int row, int column) {
        int clampedRow = Mth.clamp(row, 0, cells - 1);
        int clampedColumn = Mth.clamp(column, 0, cells - 1);
        return heights[clampedRow * cells + clampedColumn];
    }

    private static boolean shouldUseUndergroundView(Minecraft minecraft, ClientLevel level) {
        if (minecraft.player == null) {
            return false;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, playerPos.getX(), playerPos.getZ()) - 1;
        return playerPos.getY() + CAVE_TRIGGER_DEPTH < surfaceY && !level.canSeeSky(playerPos.above());
    }

    private static boolean isSurfacePassThrough(BlockState state) {
        return state.isAir() || (!state.blocksMotion() && state.getFluidState().isEmpty());
    }

    private static boolean isCaveAir(BlockState state) {
        return state.isAir() || (!state.blocksMotion() && state.getFluidState().isEmpty());
    }

    private static void renderWaypoints(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, boolean detailed, float heightScale, int refY, boolean circular) {
        if (minecraft.player == null) {
            return;
        }

        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        double edgeLimit = view.size() / 2.0D - 4.0D;

        List<MinimapWaypoint> waypoints = MinimapClientState.getWaypoints(minecraft).stream()
                .filter(waypoint -> waypoint.dimensionKey().equals(MinimapClientState.getCurrentDimensionKey(minecraft)))
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

    private static void drawPlayerMarker(GuiGraphics guiGraphics, Minecraft minecraft, MapView view, double centerX, double centerZ, float heightScale, int refY, boolean circular) {
        int centerPxX = view.x() + view.size() / 2;
        int centerPxY = view.y() + view.size() / 2;
        
        double offsetX = ((minecraft.player.getX() - centerX) / view.blocksPerCell()) * view.cellSize();
        double offsetY = ((minecraft.player.getZ() - centerZ) / view.blocksPerCell()) * view.cellSize();
        
        // Circular or square bounds check
        double radius = view.size() / 2.0D;
        if (circular) {
            if (offsetX * offsetX + offsetY * offsetY > radius * radius) return;
        } else {
            if (Math.abs(offsetX) > radius || Math.abs(offsetY) > radius) return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerPxX + offsetX, centerPxY + offsetY, 0.0F);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0F + minecraft.player.getYRot()));
        guiGraphics.pose().scale(0.9F, 0.9F, 1.0F);

        // Sharper arrow marker
        guiGraphics.fill(-2, -2, 3, 3, 0x99000000); // Shadow
        guiGraphics.fill(-1, -4, 2, 4, 0xFFFFFFFF); // Body
        guiGraphics.fill(-1, -4, 2, -2, 0xFFD42E2E); // Head
        guiGraphics.fill(-1, 2, 2, 4, 0xFF4287F5);   // Tail

        guiGraphics.pose().popPose();
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
            drawCircle(guiGraphics, cx + 2, cy + 2, radius + 5 + i, 0x11000000);
        }

        // Main frame layers (Polished bronze/steel look)
        drawCircle(guiGraphics, cx, cy, radius + 5, 0xFF1A1410); // Base dark
        drawCircle(guiGraphics, cx, cy, radius + 4, 0xFF7D5F39); // Bronze rim
        drawCircle(guiGraphics, cx, cy, radius + 2, 0xFF0D0A08); // Inner gap
        drawCircle(guiGraphics, cx, cy, radius + 1, 0xFF36281E); // Inner thin line
        
        // Anti-aliased black background for the map circle
        drawCircle(guiGraphics, cx, cy, radius, 0xFF000000);
    }

    private static void drawCircle(GuiGraphics guiGraphics, int cx, int cy, int radius, int color) {
        int x = radius;
        int y = 0;
        int err = 0;

        while (x >= y) {
            drawCirclePoints(guiGraphics, cx, cy, x, y, color);
            y++;
            err += 1 + 2 * y;
            if (2 * (err - x) + 1 > 0) {
                x--;
                err += 1 - 2 * x;
            }
        }
    }

    private static void drawCirclePoints(GuiGraphics guiGraphics, int cx, int cy, int x, int y, int color) {
        guiGraphics.hLine(cx - x, cx + x, cy - y, color);
        guiGraphics.hLine(cx - x, cx + x, cy + y, color);
        guiGraphics.hLine(cx - y, cx + y, cy - x, color);
        guiGraphics.hLine(cx - y, cx + y, cy + x, color);
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
        guiGraphics.drawString(font, text, 0, 0, color, true);
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

    public record MapView(int x, int y, int size, int cellSize, int blocksPerCell) {
        public int cells() {
            return this.size / this.cellSize;
        }
    }
}
