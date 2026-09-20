/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.mixin;

import com.chunkworks.mobilecamp.CampSites;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Protects attached block identity, including unloaded anchors and command/world mutations. Normal
 * same-block state changes (doors, furnace light, beds) remain vanilla.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(
            method =
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void camp$set(
            BlockPos p,
            BlockState state,
            int flags,
            int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        if (!CampSites.editing()
                && (Object) this instanceof ServerLevel level
                && !level.restoringBlockSnapshots
                && CampSites.get(level).owner(p) != null
                && level.getBlockState(p).getBlock() != state.getBlock()) cir.setReturnValue(false);
    }

    @Inject(
            method =
                    "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void camp$destroy(
            BlockPos p,
            boolean drops,
            Entity entity,
            int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        if (!CampSites.editing()
                && (Object) this instanceof ServerLevel level
                && CampSites.get(level).owner(p) != null) cir.setReturnValue(false);
    }
}
