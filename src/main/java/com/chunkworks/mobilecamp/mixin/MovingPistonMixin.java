/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.mixin;

import com.chunkworks.mobilecamp.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Finish actual piston animation without allowing unrelated edits into reserved sites. */
@Mixin(PistonMovingBlockEntity.class)
abstract class MovingPistonMixin {
    @WrapMethod(method="tick")
    private static void camp$tick(Level level,BlockPos pos,BlockState state,PistonMovingBlockEntity entity,Operation<Void> original) {
        if(CampMechanism.moving(level,pos))CampSites.edit(()->{original.call(level,pos,state,entity);});
        else original.call(level,pos,state,entity);
    }

    @WrapMethod(method="finalTick")
    private void camp$finish(Operation<Void> original) {
        var self=(PistonMovingBlockEntity)(Object)this;
        if(CampMechanism.moving(self.getLevel(),self.getBlockPos()))CampSites.edit(()->{original.call();});
        else original.call();
    }
}
