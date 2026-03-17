package com.ashwake.core.client.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class MinimapScreen extends Screen {
    private double mapCenterX;
    private double mapCenterZ;
    private double currentZoomIndex = -1;
    private boolean isDragging;
    private double lastMouseX;
    private double lastMouseY;

    private boolean initialized;
    private int waypointScroll;

    // Waypoint Menu
    private boolean showWaypointMenu;
    private MinimapWaypoint selectedWaypoint;
    private int pendingWorldX;
    private int pendingWorldY;
    private int pendingWorldZ;
    private EditBox waypointNameField;
    private Button saveButton;
    private Button removeButton;
    private Button cancelButton;

    public MinimapScreen() {
        super(Component.translatable("screen.ashwake_core.minimap"));
    }

    @Override
    protected void init() {
        if (!this.initialized && this.minecraft != null && this.minecraft.player != null) {
            this.mapCenterX = this.minecraft.player.getX();
            this.mapCenterZ = this.minecraft.player.getZ();
            this.initialized = true;
        }

        this.waypointNameField = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Name"));
        this.waypointNameField.setMaxLength(32);
        this.waypointNameField.setVisible(false);
        this.addRenderableWidget(this.waypointNameField);

        this.saveButton = Button.builder(Component.literal("Save"), button -> {
            this.saveWaypoint();
        }).size(48, 20).build();
        this.saveButton.visible = false;
        this.addRenderableWidget(this.saveButton);

        this.removeButton = Button.builder(Component.literal("Delete"), button -> {
            this.deleteWaypoint();
        }).size(48, 20).build();
        this.removeButton.visible = false;
        this.addRenderableWidget(this.removeButton);

        this.cancelButton = Button.builder(Component.literal("X"), button -> {
            this.closeWaypointMenu();
        }).size(20, 20).build();
        this.cancelButton.visible = false;
        this.addRenderableWidget(this.cancelButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw our own background tint instead of calling renderBackground
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xB0120D0A, 0xD0060404);

        if (this.currentZoomIndex == -1) {
            this.currentZoomIndex = MinimapClientState.getZoomIndex();
        }

        // Simple smooth transition for zoom
        int targetZoomIndex = MinimapClientState.getZoomIndex();
        if (Math.abs(this.currentZoomIndex - targetZoomIndex) > 0.001) {
            this.currentZoomIndex += (targetZoomIndex - this.currentZoomIndex) * 0.2;
        } else {
            this.currentZoomIndex = targetZoomIndex;
        }

        float actualZoom = MinimapClientState.getZoom(this.currentZoomIndex);
        MinimapRenderUtil.MapView view = MinimapRenderUtil.createFullscreenView(this.width, this.height, actualZoom);
        
        // Fullscreen map panel background
        MinimapRenderUtil.drawPanel(
                guiGraphics,
                view.x() - 12,
                view.y() - 22,
                view.size() + 24,
                view.size() + 108
        );
        
        MinimapRenderUtil.renderMap(guiGraphics, Minecraft.getInstance(), view, this.mapCenterX, this.mapCenterZ, true, false, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, view.x() + view.size() / 2, view.y() - 16, 0xFFF0E6CC);
        
        if (!this.showWaypointMenu) {
            MinimapRenderUtil.renderFullscreenFooter(guiGraphics, Minecraft.getInstance(), view);
        }

        this.renderWaypointList(guiGraphics, mouseX, mouseY, view);
        
        if (!this.showWaypointMenu) {
            LivingEntity hovered = MinimapRenderUtil.getHoveredEntity(Minecraft.getInstance(), view, this.mapCenterX, this.mapCenterZ, mouseX, mouseY, false);
            if (hovered != null) {
                guiGraphics.renderTooltip(this.font, hovered.getName(), mouseX, mouseY);
            }
        }

        if (this.showWaypointMenu) {
            // Dim background when menu is open
            guiGraphics.fill(0, 0, this.width, this.height, 0x85000000);
            
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            this.renderWaypointMenu(guiGraphics, mouseX, mouseY);
            
            // Render widgets while translated to be on top of the menu panel
            for (var renderable : this.renderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.pose().popPose();
        } else {
            // Normal widget rendering when menu is closed
            for (var renderable : this.renderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Explicitly override to do nothing, preventing some mods from applying blur effects
    }

    // Compatibility methods for various "Blur" mods to disable their effects on this screen
    public boolean blur() { return false; }
    public float getBlurAmount() { return 0.0F; }
    public int getBlurRadius() { return 0; }

    private void renderWaypointMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int width = 140;
        int height = 110;
        int x = (this.width - width) / 2;
        int y = (this.height - height) / 2;

        // Menu background
        MinimapRenderUtil.drawPanel(guiGraphics, x, y, width, height);
        
        String title = this.selectedWaypoint == null ? "New Waypoint" : "Edit Waypoint";
        guiGraphics.drawString(this.font, title, x + 8, y + 8, 0xFFF0E6CC);

        this.waypointNameField.setX(x + 8);
        this.waypointNameField.setY(y + 24);
        this.waypointNameField.setWidth(width - 16);
        this.waypointNameField.visible = true;

        int wx = this.selectedWaypoint != null ? this.selectedWaypoint.x() : this.pendingWorldX;
        int wy = this.pendingWorldY;
        int wz = this.selectedWaypoint != null ? this.selectedWaypoint.z() : this.pendingWorldZ;
        String coords = String.format("%d, %d, %d", wx, wy, wz);
        guiGraphics.drawString(this.font, coords, x + 8, y + 50, 0xFF999999, false);

        int buttonWidth = 58;
        this.saveButton.setX(x + 8);
        this.saveButton.setY(y + 76);
        this.saveButton.setWidth(buttonWidth);
        this.saveButton.visible = true;

        this.removeButton.setX(x + width - buttonWidth - 8);
        this.removeButton.setY(y + 76);
        this.removeButton.setWidth(buttonWidth);
        this.removeButton.visible = this.selectedWaypoint != null;
        
        this.cancelButton.setX(x + width - 22);
        this.cancelButton.setY(y + 2);
        this.cancelButton.visible = true;
    }

    private void renderWaypointList(GuiGraphics guiGraphics, int mouseX, int mouseY, MinimapRenderUtil.MapView view) {
        if (this.minecraft == null) return;
        
        int listWidth = 140;
        int listX = view.x() + view.size() + 20;
        int listY = view.y() - 22;
        int listHeight = view.size() + 108;

        if (listX + listWidth > this.width) {
            return;
        }

        MinimapRenderUtil.drawPanel(guiGraphics, listX, listY, listWidth, listHeight);
        guiGraphics.drawString(this.font, "Waypoints", listX + 8, listY + 8, 0xFFF0E6CC);

        List<MinimapWaypoint> waypoints = MinimapClientState.getWaypointsForCurrentDimension(this.minecraft);
        int startY = listY + 24;
        int itemHeight = 32;

        guiGraphics.enableScissor(listX, startY, listX + listWidth, listY + listHeight - 6);

        for (int i = 0; i < waypoints.size(); i++) {
            MinimapWaypoint wp = waypoints.get(i);
            int itemY = startY + i * itemHeight - this.waypointScroll;
            
            if (itemY + itemHeight < startY || itemY > listY + listHeight) continue;

            boolean hovered = !this.showWaypointMenu && mouseX >= listX + 4 && mouseX < listX + listWidth - 4 && mouseY >= itemY && mouseY < itemY + itemHeight - 2;
            int bgColor = hovered ? 0x40FFFFFF : 0x15FFFFFF;
            guiGraphics.fill(listX + 4, itemY, listX + listWidth - 4, itemY + itemHeight - 2, bgColor);
            
            guiGraphics.drawString(this.font, wp.name(), listX + 8, itemY + 4, 0xFFFFFFFF);
            String pos = String.format("%d, %d, %d", wp.x(), wp.y(), wp.z());
            guiGraphics.drawString(this.font, pos, listX + 8, itemY + 16, 0xFF999999, false);

            if (hovered) {
                 guiGraphics.drawString(this.font, "Right-click to edit", mouseX + 8, mouseY, 0xFFAAAAAA, true);
            }
        }

        guiGraphics.disableScissor();

        // Scrollbar if needed
        int totalHeight = waypoints.size() * itemHeight;
        int visibleHeight = listHeight - 30;
        if (totalHeight > visibleHeight) {
            int scrollbarX = listX + listWidth - 5;
            int scrollbarHeight = Math.max(10, (int) ((float) visibleHeight * visibleHeight / totalHeight));
            int scrollbarY = startY + (int) ((float) this.waypointScroll * (visibleHeight - scrollbarHeight) / (totalHeight - visibleHeight));
            guiGraphics.fill(scrollbarX, startY, scrollbarX + 3, startY + visibleHeight, 0x33000000); // Track
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarHeight, 0xFF888888); // Thumb
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.showWaypointMenu) {
            int width = 140;
            int height = 110;
            int x = (this.width - width) / 2;
            int y = (this.height - height) / 2;
            
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
                // If clicked outside menu area, close it
                // Check if we clicked the cancel button first
                if (this.cancelButton.isMouseOver(mouseX, mouseY)) {
                    this.closeWaypointMenu();
                    return true;
                }
                
                // Allow super to handle other widget clicks inside the menu
                if (super.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                this.closeWaypointMenu();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        MinimapRenderUtil.MapView view = MinimapRenderUtil.createFullscreenView(this.width, this.height);
        
        // Waypoint list selection
        int listWidth = 140;
        int listX = view.x() + view.size() + 20;
        int listY = view.y() - 22;
        int listHeight = view.size() + 108;
        if (listX + listWidth <= this.width && mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            List<MinimapWaypoint> waypoints = MinimapClientState.getWaypointsForCurrentDimension(this.minecraft);
            int startY = listY + 22;
            int itemHeight = 32;
            for (int i = 0; i < waypoints.size(); i++) {
                int itemY = startY + i * itemHeight - this.waypointScroll;
                if (mouseY >= itemY && mouseY < itemY + itemHeight - 2) {
                    MinimapWaypoint wp = waypoints.get(i);
                    if (button == 0) {
                        this.mapCenterX = wp.x();
                        this.mapCenterZ = wp.z();
                    } else if (button == 1) {
                        this.openWaypointMenu(wp.x(), wp.z(), wp);
                    }
                    return true;
                }
            }
            return true;
        }

        if (!MinimapRenderUtil.isInside(view, mouseX, mouseY) || this.minecraft == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            this.isDragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }

        if (button == 1) {
            var worldPos = MinimapRenderUtil.worldPosFromMouse(this.minecraft, view, mouseX, mouseY, this.mapCenterX, this.mapCenterZ);
            this.openWaypointMenu(worldPos.getX(), worldPos.getZ(), null);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.showWaypointMenu) {
            MinimapRenderUtil.MapView view = MinimapRenderUtil.createFullscreenView(this.width, this.height);
            int listWidth = 140;
            int listX = view.x() + view.size() + 20;
            if (listX + listWidth <= this.width && mouseX >= listX && mouseX <= listX + listWidth) {
                List<MinimapWaypoint> waypoints = MinimapClientState.getWaypointsForCurrentDimension(this.minecraft);
                int listHeight = view.size() + 108;
                int visibleHeight = listHeight - 28;
                int totalHeight = waypoints.size() * 32;
                int maxScroll = Math.max(0, totalHeight - visibleHeight);
                this.waypointScroll = Mth.clamp(this.waypointScroll - (int)(scrollY * 20), 0, maxScroll);
                return true;
            }

            if (scrollY > 0) {
                MinimapClientState.zoomIn();
            } else if (scrollY < 0) {
                MinimapClientState.zoomOut();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging && !this.showWaypointMenu) {
            float actualZoom = MinimapClientState.getZoom(this.currentZoomIndex);
            MinimapRenderUtil.MapView view = MinimapRenderUtil.createFullscreenView(this.width, this.height, actualZoom);
            double blocksPerPixel = 1.0D / actualZoom;
            
            this.mapCenterX -= dragX * blocksPerPixel;
            this.mapCenterZ -= dragY * blocksPerPixel;
            
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void openWaypointMenu(int worldX, int worldZ, MinimapWaypoint existing) {
        if (this.minecraft == null) return;
        
        this.selectedWaypoint = existing != null ? existing : MinimapClientState.findNearestWaypoint(this.minecraft, worldX, worldZ, 12);
        this.showWaypointMenu = true;
        this.pendingWorldX = worldX;
        this.pendingWorldY = this.minecraft.player != null ? (int) this.minecraft.player.getY() : 64;
        this.pendingWorldZ = worldZ;
        
        if (this.selectedWaypoint != null) {
            this.waypointNameField.setValue(this.selectedWaypoint.name());
            // Ensure we use the exact waypoint coordinates when editing
            this.pendingWorldX = this.selectedWaypoint.x();
            this.pendingWorldY = this.selectedWaypoint.y();
            this.pendingWorldZ = this.selectedWaypoint.z();
        } else {
            this.waypointNameField.setValue("New Waypoint");
        }
        
        this.setFocused(this.waypointNameField);
        this.waypointNameField.setFocused(true);
    }

    private void saveWaypoint() {
        if (this.minecraft == null) return;
        
        String name = this.waypointNameField.getValue();
        if (this.selectedWaypoint != null) {
            MinimapClientState.updateWaypointName(this.minecraft, this.selectedWaypoint, name);
        } else {
            MinimapClientState.addWaypointWithName(this.minecraft, this.pendingWorldX, this.pendingWorldY, this.pendingWorldZ, name);
        }
        
        this.closeWaypointMenu();
    }

    private void deleteWaypoint() {
        if (this.minecraft != null && this.selectedWaypoint != null) {
            MinimapClientState.removeWaypoint(this.minecraft, this.selectedWaypoint);
        }
        this.closeWaypointMenu();
    }

    private void closeWaypointMenu() {
        this.showWaypointMenu = false;
        this.selectedWaypoint = null;
        this.waypointNameField.visible = false;
        this.saveButton.visible = false;
        this.removeButton.visible = false;
        this.cancelButton.visible = false;
        this.setFocused(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.showWaypointMenu) {
            if (keyCode == 256) { // Escape key
                this.closeWaypointMenu();
                return true;
            }
            if (this.waypointNameField.canConsumeInput()) {
                if (keyCode == 257) { // Enter key
                    this.saveWaypoint();
                    return true;
                }
                if (super.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }

        if (MinimapKeyMappings.OPEN_MINIMAP.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (MinimapClientState.getWorldMap() != null) {
            MinimapClientState.getWorldMap().saveAll();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }
}
