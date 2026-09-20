/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.client;

import com.chunkworks.mobilecamp.MobileCamp;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Client-only registration; dedicated servers never load renderer classes. */
@EventBusSubscriber(modid = MobileCamp.ID, value = Dist.CLIENT)
public final class CampClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(MobileCamp.CORE.get(), CampRenderer::new);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent e) {
        e.register(MobileCamp.MENU.get(), CampScreen::new);
    }
}
