/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * effects: the server validates and places the complete camp; the client only
     * acknowledges the click. Rejection retains the carrier and all nested cargo.
     */
    @Override
    public InteractionResult place(BlockPlaceContext context) {
        // A client cannot validate ownership or the full reserved site. Vanilla
        // BlockItem prediction consumes its stack before a server-only rejection,
        // and unchanged server inventory otherwise produces no correction packet.
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
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
                return reject(context);
            }
            var site =
                    CampSite.inspect(
                            level,
                            context.getClickedPos(),
                            context.getHorizontalDirection().get2DDataValue());
            if (site.failure() != null) {
                if (context.getPlayer() != null)
                    context.getPlayer().displayClientMessage(site.failure(), true);
                return reject(context);
            }
        }
        ORIGINAL.set(context.getLevel().getBlockState(context.getClickedPos()));
        try {
            var result = super.place(context);
            return result == InteractionResult.FAIL ? reject(context) : result;
        } finally {
            ORIGINAL.remove();
        }
    }

    private static InteractionResult reject(BlockPlaceContext context) {
        // Also repair prediction from clients still running the previous version.
        // Force the authoritative inventory, including the offhand and components;
        // diff-only broadcastChanges sees no change on a rejected placement.
        if (context.getPlayer() instanceof ServerPlayer player)
            player.inventoryMenu.sendAllDataToRemote();
        return InteractionResult.FAIL;
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
