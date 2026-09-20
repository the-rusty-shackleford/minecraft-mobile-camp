/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/**
 * Partitions: solid/wet/deep-footing rejection, both hands, cargo-bearing carriers.
 * Real survival ServerPlayer, game-mode item interaction and vanilla outbound
 * inventory packets. Diff-only synchronization must not hide a rejected camp.
 */
@GameTestHolder("mobilecamp")
@PrefixGameTestTemplate(false)
public final class PlacementGameTests {
    private static final BlockPos CORE = new BlockPos(10, 2, 7);

    @GameTest(template = "arena", timeoutTicks = 30, batch = "placement-sync")
    public void rejectionResendsUnchangedCampAndCargoInBothHands(GameTestHelper h) {
        for (int x = 0; x < 25; x++)
            for (int z = 0; z < 25; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var player = PlacementPlayers.player(h, 10, 5);
        try {
            for (var hand : InteractionHand.values()) {
                for (int obstruction = 0; obstruction < 3; obstruction++) {
                    var stack = ladenCamp(h);
                    var expected = stack.copy();
                    player.setItemInHand(hand, stack);
                    player.inventoryMenu.sendAllDataToRemote();
                    PlacementPlayers.packets(player);
                    var obstacle = CORE.offset(3, 1, 5);
                    h.setBlock(obstacle, obstruction == 0 ? Blocks.STONE
                            : obstruction == 1 ? Blocks.WATER : Blocks.AIR);
                    if (obstruction == 2)
                        for (int depth = 1; depth <= 4; depth++)
                            h.setBlock(CORE.offset(-3, -depth, 0), Blocks.AIR);
                    var ground = h.absolutePos(CORE.below());
                    var result = player.gameMode.useItemOn(player, h.getLevel(), stack, hand,
                            new BlockHitResult(Vec3.atCenterOf(ground).add(0, .5, 0),
                                    net.minecraft.core.Direction.UP, ground, false));
                    player.inventoryMenu.broadcastChanges();
                    var packets = PlacementPlayers.packets(player);
                    h.assertTrue(result == InteractionResult.FAIL, "incompatible site refuses");
                    h.assertTrue(ItemStack.matches(expected, player.getItemInHand(hand)),
                            "rejection leaves exact carrier and cargo on server");
                    h.assertTrue(packets.stream().anyMatch(packet ->
                                    packet instanceof ClientboundContainerSetContentPacket contents
                                            && contents.getContainerId() == 0
                                            && ItemStack.matches(expected, contents.getItems().get(
                                                    hand == InteractionHand.MAIN_HAND ? 36 : 45))),
                            "rejection corrects a client's predicted empty slot: " + hand + "/" + obstruction);
                    h.assertTrue(CampOwners.get(h.getLevel()).active(player.getUUID()) == null,
                            "rejection reserves no ownership");
                    h.assertTrue(CampSites.get(h.getLevel()).owner(h.absolutePos(CORE)) == null,
                            "rejection reserves no terrain");
                    h.assertBlockNotPresent(MobileCamp.CAMP.get(), CORE);
                    h.setBlock(CORE.offset(-3, -1, 0), Blocks.STONE);
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
            h.succeed();
        } finally {
            PlacementPlayers.remove(player);
        }
    }

    static ItemStack ladenCamp(GameTestHelper h) {
        return ladenCamp(h.getLevel().registryAccess());
    }

    static ItemStack ladenCamp(net.minecraft.core.HolderLookup.Provider registries) {
        var camp = new ItemStack(MobileCamp.CAMP_ITEM.get());
        camp.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition supplies"));
        var modules = CampCargo.read(camp, registries);
        var inventory = new ListTag();
        var diamond = (CompoundTag) new ItemStack(Items.DIAMOND, 17).save(registries);
        diamond.putByte("Slot", (byte) 8);
        inventory.add(diamond);
        var entity = new CompoundTag();
        entity.put("Items", inventory);
        var cell = new CompoundTag();
        cell.put("Entity", entity);
        var cargo = new CompoundTag();
        cargo.put("0,0,0", cell);
        var data = new CompoundTag();
        data.put("Cargo", cargo);
        CampCargo.data(modules.get(1), data);
        CampCargo.write(camp, modules, registries);
        return camp;
    }
}
