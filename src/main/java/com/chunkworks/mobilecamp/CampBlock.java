/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

/** Camp anchor. Ordinary placement starts deployment; mining requests safe retraction. */
public final class CampBlock extends BaseEntityBlock {
    public static final MapCodec<CampBlock> CODEC = simpleCodec(CampBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new CampBlockEntity(p, s);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level l, BlockState s, BlockEntityType<T> t) {
        return createTickerHelper(t, MobileCamp.CORE.get(), CampBlockEntity::tick);
    }

    @Override
    public void setPlacedBy(
            Level l, BlockPos p, BlockState s, LivingEntity placer, ItemStack stack) {
        if (!l.isClientSide
                && placer instanceof Player player
                && l.getBlockEntity(p) instanceof CampBlockEntity camp) camp.begin(stack, player);
    }

    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState next, boolean piston) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof CampBlockEntity camp)
            camp.rollbackPlacement();
        super.onRemove(state, level, pos, next, piston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState s, Level l, BlockPos p, Player player, BlockHitResult hit) {
        if (!l.isClientSide && l.getBlockEntity(p) instanceof CampBlockEntity camp)
            camp.requestPack(player);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
