/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.client;

import com.chunkworks.mobilecamp.*;
import com.chunkworks.mobilecamp.domain.Shelter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Choreographed unfolding: sliding deck, hinged walls, rising equipment, spreading canvas. AF:
 * render-only moving parts between packed and their actual placed positions. RI: no inventories
 * transmitted; active camps render their real blocks, never duplicate ghosts.
 */
public final class CampRenderer implements BlockEntityRenderer<CampBlockEntity> {
    private final BlockEntityRendererProvider.Context context;
    private final Map<CampBlockEntity, Map<BlockPos, BlockEntity>> renderEntities =
            new WeakHashMap<>();

    public CampRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public boolean shouldRenderOffScreen(CampBlockEntity c) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CampBlockEntity c) {
        return c.bounds().inflate(1);
    }

    @Override
    public void render(
            CampBlockEntity camp,
            float partial,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        if (camp.mechanical() || camp.phase() == CampBlockEntity.Phase.ACTIVE) return;
        double ticks =
                camp.progress()
                        + (camp.paused()
                                ? 0
                                : partial
                                        * (camp.phase() == CampBlockEntity.Phase.FOLDING ? -1 : 1));
        for (var cell : camp.plan().cells()) {
            double p = Shelter.progress(cell.mechanism(), ticks);
            if (p <= 0) continue;
            var offset = cell.offset();
            pose.pushPose();
            double x = offset.getX(), y = offset.getY(), z = offset.getZ();
            switch (cell.mechanism()) {
                case DECK -> {
                    pose.translate(x * p, 0, z * p);
                    pose.translate(.5, .5, .5);
                    pose.mulPose(
                            Axis.XP.rotationDegrees(
                                    (float) ((1 - p) * 90 * (offset.getZ() % 2 == 0 ? 1 : -1))));
                    pose.translate(-.5, -.5, -.5);
                }
                case WALL, WINDOW -> {
                    var local =
                            new Shelter.Pos(offset.getX(), offset.getY(), offset.getZ())
                                    .rotate(-camp.turns());
                    pose.translate(x + .5, 1, z + .5);
                    pose.mulPose(Axis.YP.rotationDegrees(-camp.turns() * 90));
                    float angle = (float) ((1 - p) * 90);
                    if (local.x() == -3 || local.x() == 4)
                        pose.mulPose(Axis.ZP.rotationDegrees(local.x() == -3 ? -angle : angle));
                    else pose.mulPose(Axis.XP.rotationDegrees(angle));
                    pose.mulPose(Axis.YP.rotationDegrees(camp.turns() * 90));
                    pose.translate(-.5, y - 1, -.5);
                }
                case POST -> pose.translate(x, 1 + (y - 1) * p, z);
                case LANTERN -> pose.translate(x * p, y * p, z * p);
                case CANVAS -> {
                    var local =
                            new Shelter.Pos(offset.getX(), offset.getY(), offset.getZ())
                                    .rotate(-camp.turns());
                    pose.translate(.5, 0, .5);
                    pose.mulPose(Axis.YP.rotationDegrees(-camp.turns() * 90));
                    pose.translate(.5, 5.65685, local.z());
                    pose.mulPose(
                            Axis.ZP.rotationDegrees(
                                    (float) ((1 - p) * 90 * (local.x() <= 0 ? 1 : -1))));
                    pose.translate(local.x() - 1, -1.65685, -.5);
                    pose.translate(.5, 0, .5);
                    pose.mulPose(Axis.YP.rotationDegrees(camp.turns() * 90));
                    pose.translate(-.5, 0, -.5);
                }
            }
            if (cell.state().getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED
                    && cell.state().getBlock() instanceof EntityBlock block) {
                var map = renderEntities.computeIfAbsent(camp, k -> new HashMap<>());
                var entity =
                        map.computeIfAbsent(
                                offset,
                                k ->
                                        block.newBlockEntity(
                                                camp.getBlockPos().offset(k), cell.state()));
                if (entity != null) {
                    entity.setLevel(camp.getLevel());
                    context.getBlockEntityRenderDispatcher()
                            .renderItem(entity, pose, buffers, light, overlay);
                }
            } else
                context.getBlockRenderDispatcher()
                        .renderSingleBlock(cell.state(), pose, buffers, light, overlay);
            pose.popPose();
        }
        double legs = Math.clamp(ticks / 20, 0, 1);
        if (legs > 0)
            for (var foot : camp.supports()) {
                var off = foot.subtract(camp.getBlockPos());
                pose.pushPose();
                pose.translate(off.getX(), off.getY() * legs, off.getZ());
                context.getBlockRenderDispatcher()
                        .renderSingleBlock(
                                MobileCamp.DECK.get().defaultBlockState(),
                                pose,
                                buffers,
                                light,
                                overlay);
                pose.popPose();
            }
    }
}
