/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;

/** Input adapters prevent drops before vanilla destruction and protect occupied transformations. */
@EventBusSubscriber(modid = MobileCamp.ID)
public final class CampProtection {
    private CampProtection() {}

    @SubscribeEvent
    public static void mine(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var owner = CampSites.get(level).owner(event.getPos());
        if (owner == null) return;
        event.setCanceled(true);
        if (owner.equals(event.getPos())
                && level.getBlockEntity(owner) instanceof CampBlockEntity core)
            core.requestPack(event.getPlayer());
        else
            event.getPlayer()
                    .displayClientMessage(Component.translatable("camp.mobilecamp.attached"), true);
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level)
            event.getAffectedBlocks().removeIf(p -> CampSites.get(level).owner(p) != null);
    }

    @SubscribeEvent
    public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var sites = CampSites.get(level);
        var resolver = event.getStructureHelper();
        if (sites.owner(event.getFaceOffsetPos()) != null) {
            event.setCanceled(true);
            return;
        }
        if (resolver == null) return;
        resolver.resolve();
        for (var p : resolver.getToPush())
            if (sites.owner(p) != null || sites.owner(p.relative(event.getDirection())) != null) {
                event.setCanceled(true);
                return;
            }
        for (var p : resolver.getToDestroy())
            if (sites.owner(p) != null) {
                event.setCanceled(true);
                return;
            }
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var anchor = CampSites.get(level).owner(event.getPos());
        if (anchor == null) return;
        if (level.getBlockEntity(anchor) instanceof CampBlockEntity camp) {
            if (anchor.equals(event.getPos())) {
                if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
                    camp.requestPack(event.getEntity());
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            if (camp.phase() != CampBlockEntity.Phase.ACTIVE) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
        }
        if (level.getBlockState(event.getPos()).getBlock() instanceof BedBlock
                && !level.dimensionType().bedWorks()) {
            event.getEntity()
                    .displayClientMessage(Component.translatable("camp.mobilecamp.no_sleep"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
