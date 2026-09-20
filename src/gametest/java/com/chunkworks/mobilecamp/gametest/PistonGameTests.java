/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;
import com.chunkworks.mobilecamp.domain.Shelter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.function.Consumer;

/** Partitions: every layer/wing moves through vanilla piston entities; redstone;
 * mid-stroke core reload; occupied actuator strip; external cancellation with exact
 * packed recovery. Existing tests cover rotated cargo and complete terrain recovery. */
@GameTestHolder("mobilecamp")
@PrefixGameTestTemplate(false)
public final class PistonGameTests {
    private static final BlockPos CORE=new BlockPos(10,2,7);

    @GameTest(template="arena",batch="camp-pistons",timeoutTicks=400)
    public void allWingsUseActualPistonsAndSurviveReloadBeforePacking(GameTestHelper h) {
        var p=CampFeatureGameTests.place(h);
        boolean[] strokes=new boolean[10],reverse=new boolean[10],mount={false},power={false},observing={true};
        h.onEachTick(()->{
            if(!observing[0])return;
            var core=(CampBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(CORE));
            if(core==null)return;
            boolean folding=core.phase()==CampBlockEntity.Phase.FOLDING;
            for(int layer=0;layer<5;layer++)for(int side=0;side<2;side++) {
                int x=folding?(side==0?3:-2):(side==0?4:-3);
                if(h.getLevel().getBlockEntity(h.absolutePos(CORE.offset(x,layer,layer==0?1:0))) instanceof PistonMovingBlockEntity) {
                    if(folding)reverse[layer*2+side]=true;
                    else strokes[layer*2+side]=true;
                }
            }
            if(h.getLevel().getBlockEntity(h.absolutePos(CORE.offset(2,0,1))) instanceof PistonMovingBlockEntity moving
                    &&moving.getMovedState().is(MobileCamp.MOUNT.get()))mount[0]=true;
            power[0]|=h.getBlockState(CORE.offset(0,1,1)).is(Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(11,()->{
            var old=(CampBlockEntity)h.getBlockEntity(CORE);
            var saved=old.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(old.getBlockPos());
            var restored=(CampBlockEntity)BlockEntity.loadStatic(old.getBlockPos(),old.getBlockState(),saved,h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            h.assertTrue(restored.mechanical()&&restored.progress()==old.progress(),"mid-stroke core resumes its mechanical state");
        });
        h.runAtTickTime(175,()->{
            for(int i=0;i<10;i++)h.assertTrue(strokes[i],"deployment layer/wing "+i+" used a real moving-piston entity");
            h.assertTrue(mount[0]&&power[0],"vanilla redstone pushes actual module mount");
            var camp=(CampBlockEntity)h.getBlockEntity(CORE);
            h.assertTrue(camp.phase()==CampBlockEntity.Phase.ACTIVE,"all real strokes complete");
            camp.requestPack(p);
        });
        h.runAtTickTime(350,()->{
            observing[0]=false;
            try {
                for(int i=0;i<10;i++)h.assertTrue(reverse[i],"packing layer/wing "+i+" used a real moving-piston entity");
                h.assertBlockNotPresent(MobileCamp.CAMP.get(),CORE);
                var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(CORE)).inflate(12));
                h.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(MobileCamp.CAMP_ITEM.get()),"one pickup, no loose pistons/power/mounts");
                h.succeed();
            } finally { PlacementPlayers.remove(p); }
        });
    }

    @GameTest(template="arena",batch="camp-piston-cancel",timeoutTicks=60)
    public void cancelledPistonReturnsCargoAndRestoresAllReservedTerrain(GameTestHelper h) {
        for(int x=0;x<25;x++)for(int z=0;z<25;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var p=PlacementPlayers.player(h,10,5);
        var carrier=PlacementGameTests.ladenCamp(h);
        var expected=carrier.copy();
        Consumer<PistonEvent.Pre> cancel=e->{
            if(e.getLevel()==h.getLevel()&&h.absolutePos(CORE).equals(CampSites.get(h.getLevel()).owner(e.getPos())))
                e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,carrier);
        var ground=h.absolutePos(CORE.below());
        p.gameMode.useItemOn(p,h.getLevel(),carrier,net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0,.5,0),net.minecraft.core.Direction.UP,ground,false));
        h.runAtTickTime(40,()->{
            try {
                h.assertBlockNotPresent(MobileCamp.CAMP.get(),CORE);
                var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(CORE)).inflate(12));
                h.assertTrue(drops.size()==1&&net.minecraft.world.item.ItemStack.matches(expected,drops.getFirst().getItem()),
                        "cancelled stroke returns exact carrier with nested cargo");
                for(var position:CampMechanism.volume(h.absolutePos(CORE),0)) {
                    h.assertTrue(h.getLevel().getBlockState(position).isAir(),"reserved terrain restored: "+position);
                    h.assertTrue(CampSites.get(h.getLevel()).owner(position)==null,"reservation released");
                }
                h.assertTrue(CampOwners.get(h.getLevel()).active(p.getUUID())==null,"owner released");
                h.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(cancel);PlacementPlayers.remove(p); }
        });
    }

    @GameTest(template="arena",batch="camp-piston-occupied",timeoutTicks=260)
    public void actuatorStripOccupantsPauseAndThenResumeAssembly(GameTestHelper h) {
        var p=CampFeatureGameTests.place(h);
        var cow=EntityType.COW.create(h.getLevel());
        int[] stopped={0};
        h.runAtTickTime(20,()->{
            cow.setNoAi(true);cow.setNoGravity(true);
            cow.moveTo(Vec3.atBottomCenterOf(h.absolutePos(CORE.offset(5,0,4))));
            h.getLevel().addFreshEntity(cow);
            stopped[0]=((CampBlockEntity)h.getBlockEntity(CORE)).progress();
        });
        h.runAtTickTime(45,()->{
            var camp=(CampBlockEntity)h.getBlockEntity(CORE);
            h.assertTrue(camp.paused()&&camp.progress()<=stopped[0]+1,"actuator strip pauses new strokes");
            cow.discard();
        });
        h.runAtTickTime(215,()->{
            try {
                h.assertTrue(((CampBlockEntity)h.getBlockEntity(CORE)).phase()==CampBlockEntity.Phase.ACTIVE,
                        "assembly resumes after occupant leaves");
                h.succeed();
            } finally {cow.discard();PlacementPlayers.remove(p);}
        });
    }
}
