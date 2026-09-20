/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four equipment bays; unused container cells are concealed and server-inaccessible. */
public final class CampScreen extends ContainerScreen {
    public CampScreen(
            net.minecraft.world.inventory.ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        super.renderBg(graphics, partial, x, y);
        graphics.fill(leftPos + 79, topPos + 17, leftPos + 169, topPos + 35, 0xffc6c6c6);
        graphics.drawString(
                font,
                Component.translatable("camp.mobilecamp.bay_hint"),
                leftPos + 82,
                topPos + 22,
                0xff555555,
                false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (hoveredSlot != null && hoveredSlot.index < 4 && hoveredSlot.getItem().isEmpty()) {
            graphics.renderTooltip(
                    font, Component.translatable("camp.mobilecamp.bay." + hoveredSlot.index), x, y);
        } else super.renderTooltip(graphics, x, y);
    }
}
