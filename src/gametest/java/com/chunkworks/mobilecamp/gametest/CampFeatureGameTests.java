/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

/** Partitions: deployed control versus packed editing; valid/invalid bay inputs;
 * cargo removal/reinsert; actual night sleep without respawn changes; ordinary bed
 * still updates spawn. Uses real survival players and vanilla interaction/menu paths. */
@GameTestHolder("mobilecamp")
@PrefixGameTestTemplate(false)
public final class CampFeatureGameTests {
    private static final BlockPos CORE = new BlockPos(10,2,7);

    static ServerPlayer place(GameTestHelper h) {
        for(int x=0;x<25;x++)for(int z=0;z<25;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var p=PlacementPlayers.player(h,10,5);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(MobileCamp.CAMP_ITEM.get()));
        use(h,p,CORE.below(),Direction.UP);
        h.assertBlockPresent(MobileCamp.CAMP.get(),CORE);
        return p;
    }

    private static void use(GameTestHelper h,ServerPlayer p,BlockPos pos,Direction face) {
        var absolute=h.absolutePos(pos);
        p.gameMode.useItemOn(p,h.getLevel(),p.getMainHandItem(),InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),
                        face,absolute,false));
    }

    @GameTest(template="arena",batch="camp-module-control",timeoutTicks=220)
    public void deployedControlEditsLiveCargoThroughRealSlots(GameTestHelper h) {
        var p=place(h);
        h.runAtTickTime(175,()->{
            try {
                var chestPos=CORE.offset(2,1,1);
                var chest=(ChestBlockEntity)h.getBlockEntity(chestPos);
                chest.setItem(8,new ItemStack(Items.DIAMOND,17));
                use(h,p,CORE.offset(1,0,0),Direction.NORTH);
                h.assertTrue(p.containerMenu instanceof CampMenu,"ordinary right click opens module editor");
                var menu=(CampMenu)p.containerMenu;
                h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT))
                        &&!menu.getSlot(0).mayPlace(new ItemStack(MobileCamp.STORAGE.get()))
                        &&menu.getSlot(1).mayPlace(new ItemStack(MobileCamp.STORAGE.get())),
                        "actual menu slots enforce module type and bay");
                for(int i=4;i<9;i++)h.assertTrue(!menu.getSlot(i).mayPlace(new ItemStack(Items.DIRT)),
                        "unused slot rejects items");
                menu.clicked(1,0,ClickType.PICKUP,p);
                h.assertTrue(menu.getCarried().is(MobileCamp.STORAGE.get()),"storage module picked up");
                h.assertBlockNotPresent(Blocks.CHEST,chestPos);
                var items=CampCargo.data(menu.getCarried()).getCompound("Cargo").getCompound("0,0,0")
                        .getCompound("Entity").getList("Items",10);
                h.assertTrue(items.size()==1&&items.getCompound(0).getInt("count")==17,
                        "removed module includes current live chest contents");
                ((CampBlockEntity)h.getBlockEntity(CORE)).requestPack(p);
                h.assertTrue(((CampBlockEntity)h.getBlockEntity(CORE)).phase()==CampBlockEntity.Phase.ACTIVE,
                        "collapse cannot race editor");
                menu.clicked(1,0,ClickType.PICKUP,p);
                h.assertTrue(menu.getCarried().isEmpty(),"module reinserted once");
                chest=(ChestBlockEntity)h.getBlockEntity(chestPos);
                h.assertTrue(chest.getItem(8).is(Items.DIAMOND)&&chest.getItem(8).getCount()==17,
                        "reinsert restores exact cargo");
                p.closeContainer();
                ((CampBlockEntity)h.getBlockEntity(CORE)).requestPack(p);
                h.assertTrue(((CampBlockEntity)h.getBlockEntity(CORE)).phase()==CampBlockEntity.Phase.FOLDING,
                        "closing editor releases collapse lease");
                h.succeed();
            } finally {
                p.closeContainer();PlacementPlayers.remove(p);
            }
        });
    }

    @GameTest(template="arena",batch="camp-bedroll",timeoutTicks=240)
    public void bedrollSleepsWithoutReplacingHomeSpawnButNormalBedStillDoes(GameTestHelper h) {
        var p=place(h);
        // Day/night brightness updates on the world tick, not inside setDayTime.
        h.getLevel().setDayTime(13000);
        h.runAtTickTime(175,()->{
            try {
                var home=h.absolutePos(CORE.offset(0,0,-4));
                p.setRespawnPosition(Level.OVERWORLD,home,0,true,false);
                var roll=CORE.offset(-2,1,1);
                var near=h.absolutePos(roll.offset(1,0,0));
                p.moveTo(near.getX()+.5,near.getY(),near.getZ()+.5,0,0);
                h.getLevel().setDayTime(13000);
                use(h,p,roll,Direction.UP);
                h.assertTrue(p.isSleeping(),"bedroll enters actual sleeping state; day="+h.getLevel().isDay()+", messages="+
                        PlacementPlayers.packets(p).stream().filter(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class::isInstance)
                                .map(packet->((net.minecraft.network.protocol.game.ClientboundSystemChatPacket)packet).content().getString()).toList());
                h.assertTrue(home.equals(p.getRespawnPosition())&&p.getRespawnDimension()==Level.OVERWORLD,
                        "bedroll preserves home spawn");
                p.stopSleepInBed(true,true);
                var foot=CORE.offset(0,0,-3);
                h.setBlock(foot,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH));
                h.setBlock(foot.south(),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH)
                        .setValue(BedBlock.PART,BedPart.HEAD));
                near=h.absolutePos(foot.west());
                p.moveTo(near.getX()+.5,near.getY(),near.getZ()+.5,0,0);
                use(h,p,foot,Direction.UP);
                h.assertTrue(h.absolutePos(foot.south()).equals(p.getRespawnPosition()),
                        "ordinary beds retain vanilla spawn setting");
                p.stopSleepInBed(true,true);
                h.succeed();
            } finally {
                p.stopSleepInBed(true,true);PlacementPlayers.remove(p);
            }
        });
    }
}
