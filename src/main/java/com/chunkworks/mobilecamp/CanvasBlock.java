/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/**
 * Four rising canvas strips form each side of a 22.5 degree pitched shelter roof. AF: rise selects
 * distance from eave; facing points uphill. RI: rise 0..3, horizontal facing.
 */
public final class CanvasBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty RISE = IntegerProperty.create("rise", 0, 3);
    public static final BooleanProperty GABLE = BooleanProperty.create("gable");
    public static final MapCodec<CanvasBlock> CODEC = simpleCodec(CanvasBlock::new);

    public CanvasBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(RISE, 0)
                        .setValue(FACING, Direction.EAST)
                        .setValue(GABLE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(RISE, FACING, GABLE);
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        double base = s.getValue(RISE) * 6.6274;
        return Shapes.or(
                Block.box(0, s.getValue(GABLE) ? 0 : base, 0, 16, base + 8.5, 16),
                Block.box(0, 0, 7, 16, 1, 9));
    }
}
