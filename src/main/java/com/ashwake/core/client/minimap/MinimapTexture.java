package com.ashwake.core.client.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class MinimapTexture implements AutoCloseable {
    private final DynamicTexture texture;
    private final ResourceLocation location;
    private final int width;
    private final int height;

    public MinimapTexture(String name, int width, int height) {
        this.width = width;
        this.height = height;
        this.texture = new DynamicTexture(width, height, false);
        this.texture.setFilter(true, false); // LINEAR filtering for smooth zoom
        this.location = Minecraft.getInstance().getTextureManager().register(name, this.texture);
    }

    public void setPixels(int[] argbPixels, int dataWidth, int dataHeight, int stride) {
        NativeImage image = this.texture.getPixels();
        if (image == null) return;

        for (int y = 0; y < dataHeight && y < height; y++) {
            for (int x = 0; x < dataWidth && x < width; x++) {
                int argb = argbPixels[y * stride + x];
                // Convert ARGB to ABGR (which NativeImage expects for RGBA)
                int r = (argb >> 16) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (argb & 0xFF00FF00) | (b << 16) | r;
                image.setPixelRGBA(x, y, abgr);
            }
        }
        this.texture.upload();
    }

    public ResourceLocation getLocation() {
        return location;
    }

    @Override
    public void close() {
        this.texture.close();
    }
}
