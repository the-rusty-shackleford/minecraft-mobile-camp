/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Placement result. AF: verified corner legs or localized failure. RI: immutable position list;
 * validity is momentary and must be checked at actual placement.
 */
public record CampSite(List<BlockPos> supports, Component failure) {
    public CampSite {
        supports = List.copyOf(supports);
    }

    /**
     * requires: server level; effects: checks loaded clear volume and stable corner footing,
     * without modifying or loading terrain.
     */
    public static CampSite inspect(ServerLevel level, BlockPos core, int turns) {
        return inspect(level, core, turns, false);
    }

    static CampSite inspect(ServerLevel level, BlockPos core, int turns, boolean placed) {
        var supports = new ArrayList<BlockPos>();
        for (var p : CampPlan.volume(core, turns)) {
            if (placed && p.equals(core)) continue;
            if (!level.hasChunkAt(p)
                    || !level.getWorldBorder().isWithinBounds(p)
                    || level.isOutsideBuildHeight(p)) return fail("unloaded", p);
            var state = level.getBlockState(p);
            boolean vegetation =
                    state.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)
                            || state.is(net.minecraft.tags.BlockTags.TALL_FLOWERS);
            if (CampSites.get(level).owner(p) != null
                    || (!state.canBeReplaced() && !vegetation)
                    || !state.getFluidState().isEmpty()
                    || level.getBlockEntity(p) != null) return fail("blocked", p);
        }
        for (int x : new int[] {-3, 4})
            for (int z : new int[] {0, 7}) {
                var foot =
                        core.offset(CampPlan.pos(new Shelter.Pos(x, 0, z).rotate(turns))).below();
                int depth = 0;
                while (depth < 3
                        && !level.getBlockState(foot).isFaceSturdy(level, foot, Direction.UP)) {
                    if (!level.hasChunkAt(foot)
                            || !level.getBlockState(foot).canBeReplaced()
                            || !level.getFluidState(foot).isEmpty()
                            || level.getBlockEntity(foot) != null
                            || CampSites.get(level).owner(foot) != null)
                        return fail("footing", foot);
                    supports.add(foot);
                    foot = foot.below();
                    depth++;
                }
                if (level.isOutsideBuildHeight(foot)
                        || !level.getBlockState(foot).isFaceSturdy(level, foot, Direction.UP))
                    return fail("footing", foot);
            }
        return new CampSite(supports, null);
    }

    private static CampSite fail(String reason, BlockPos pos) {
        return new CampSite(
                List.of(),
                Component.translatable(
                        "camp.mobilecamp." + reason, pos.getX(), pos.getY(), pos.getZ()));
    }
}
