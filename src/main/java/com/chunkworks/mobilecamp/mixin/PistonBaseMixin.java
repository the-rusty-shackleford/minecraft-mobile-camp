/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.mixin;

import com.chunkworks.mobilecamp.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Vanilla piston movement bypasses camp protection only for its scheduled actuator. */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseMixin {
    @WrapMethod(method="triggerEvent")
    private boolean camp$stroke(BlockState state,Level level,BlockPos pos,int id,int param,Operation<Boolean> original) {
        if(!CampMechanism.actuator(level,pos))return original.call(state,level,pos,id,param);
        return CampSites.edit(()->original.call(state,level,pos,id,param));
    }
}
