/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Portable core. AF: one camp with nested module cargo; RI: maximum stack size one. */
public final class CampItem extends BlockItem {
    private static final ThreadLocal<net.minecraft.world.level.block.state.BlockState> ORIGINAL =
            new ThreadLocal<>();

    static net.minecraft.world.level.block.state.BlockState originalAtPlacement() {
        var state = ORIGINAL.get();
        return state == null
                ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : state;
    }

    public CampItem(Block block) {
        super(block, new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            if (context.getPlayer() == null) return InteractionResult.FAIL;
            var active = CampOwners.get(level).active(context.getPlayer().getUUID());
            if (active != null) {
                var p = net.minecraft.core.BlockPos.of(active.position());
                context.getPlayer()
                        .displayClientMessage(
                                Component.translatable(
                                        "camp.mobilecamp.already_deployed",
                                        active.dimension(),
                                        p.getX(),
                                        p.getY(),
                                        p.getZ()),
                                true);
                return InteractionResult.FAIL;
            }
            var site =
                    CampSite.inspect(
                            level,
                            context.getClickedPos(),
                            context.getHorizontalDirection().get2DDataValue());
            if (site.failure() != null) {
                if (context.getPlayer() != null)
                    context.getPlayer().displayClientMessage(site.failure(), true);
                return InteractionResult.FAIL;
            }
        }
        ORIGINAL.set(context.getLevel().getBlockState(context.getClickedPos()));
        try {
            return super.place(context);
        } finally {
            ORIGINAL.remove();
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide)
            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new CampMenu(id, inventory, stack),
                            Component.translatable("container.mobilecamp.modules")));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            java.util.List<Component> lines,
            TooltipFlag flag) {
        lines.add(Component.translatable("camp.mobilecamp.item_hint"));
    }
}
