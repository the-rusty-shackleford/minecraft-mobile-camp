/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * A bounded sequence of real vanilla piston strokes. AF: immutable shell lookup,
 * anchor and facing describe ten alternating three-block wing pushes. RI: each
 * stroke pushes at most three blocks; piston, head and power stay in the reserved
 * 10x8x6 volume. Equipment is installed only after the shell finishes moving.
 * The camp stores progress; vanilla moving-block entities persist their own motion.
 */
public final class CampMechanism {
    private final CampBlockEntity camp;
    private final Map<BlockPos,BlockState> shell;
    private static final int[] START = {8,20,36,48,64,76,92,104,120,132};

    public CampMechanism(CampBlockEntity camp) {
        this.camp=camp;
        var parts=new HashMap<BlockPos,BlockState>();
        for(var cell:camp.plan().cells())
            if(cell.bay()<0&&!(cell.state().getBlock() instanceof DoorBlock)
                    &&!(cell.state().getBlock() instanceof LanternBlock))
                parts.put(cell.offset(),cell.state());
        shell=Map.copyOf(parts);
    }

    /** effects: reserves room for the two exterior packing actuators, without loading terrain. */
    public static List<BlockPos> volume(BlockPos core,int turns) {
        var positions=new ArrayList<BlockPos>();
        for(int x=-4;x<=5;x++)for(int y=0;y<Shelter.HEIGHT;y++)for(int z=0;z<=7;z++)
            positions.add(core.offset(CampPlan.pos(new Shelter.Pos(x,y,z).rotate(turns))));
        return List.copyOf(positions);
    }

    private BlockPos offset(int x,int y,int z) {
        return CampPlan.pos(new Shelter.Pos(x,y,z).rotate(camp.turns()));
    }
    private BlockPos pos(int x,int y,int z) { return camp.getBlockPos().offset(offset(x,y,z)); }
    private ServerLevel level() { return (ServerLevel)camp.getLevel(); }
    private BlockState material(int x,int y,int z) {
        return shell.getOrDefault(offset(x,y,z),Blocks.AIR.defaultBlockState());
    }
    private void set(int x,int y,int z,BlockState state) { level().setBlock(pos(x,y,z),state,18); }
    private void air(int x,int y,int z) { set(x,y,z,Blocks.AIR.defaultBlockState()); }

    /** effects: advances the one scheduled stroke, using vanilla redstone and block events. */
    public boolean tick(int elapsed,boolean folding) {
        if(elapsed==1&&!folding) CampSites.edit(()->{
            for(var support:camp.supports())level().setBlock(support,MobileCamp.DECK.get().defaultBlockState(),18);
            for(int x=-3;x<=4;x++)if(x!=0)set(x,0,0,material(x,0,0));
        });
        for(int wave=0;wave<START.length;wave++) {
            int step=elapsed-START[wave];
            if(step<0||step>10)continue;
            boolean right=wave%2==0;
            int y=folding?4-wave/2:wave/2;
            int base=folding?(right?5:-4):(right?0:1);
            int direction=(right?1:-1)*(folding?-1:1);
            int layer=y;
            if(step==0) CampSites.edit(()->{
                for(int z=0;z<8;z++) {
                    if(layer==0&&z==0)continue; // The core/rear sill never moves.
                    for(int i=0;i<3;i++) {
                        int destination=right?2+i:-1-i;
                        int source=folding?destination:destination-direction;
                        var block=material(destination,layer,z);
                        // Fill gaps in a row to transmit the piston stroke. These
                        // temporary push bars are recovered after motion completes.
                        set(source,layer,z,block.isAir()?MobileCamp.DECK.get().defaultBlockState():block);
                    }
                    var facing=Rotation.values()[Math.floorMod(camp.turns(),4)]
                            .rotate(direction>0?Direction.EAST:Direction.WEST);
                    set(base,layer,z,Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING,facing));
                }
            });
            if(step==1||step==6) CampSites.edit(()->{
                for(int z=0;z<8;z++)if(layer!=0||z!=0)
                    set(base,layer+1,z,step==1?Blocks.REDSTONE_BLOCK.defaultBlockState():Blocks.AIR.defaultBlockState());
                // Notify after the entire power rail changes, including the
                // diagonal power checks used by vanilla piston quasi-connectivity.
                for(int z=0;z<8;z++)if(layer!=0||z!=0)
                    level().neighborChanged(pos(base,layer,z),Blocks.REDSTONE_BLOCK,pos(base,layer+1,z));
            });
            if(step==10 && !CampSites.edit(()->{
                // A cancelled/jammed stroke rolls the prefab back to a pickup;
                // never crash the server or silently manufacture the final shell.
                for(int z=0;z<8;z++) {
                    if(layer==0&&z==0)continue;
                    for(int i=0;i<3;i++) {
                        int destination=right?2+i:-1-i;
                        var expected=material(destination,layer,z);
                        if(folding&&expected.isAir())expected=MobileCamp.DECK.get().defaultBlockState();
                        if(!expected.isAir()&&!level().getBlockState(pos(destination+(folding?direction:0),layer,z)).is(expected.getBlock()))
                            return false;
                    }
                }
                for(int z=0;z<8;z++) {
                    if(layer==0&&z==0)continue;
                    for(int i=0;i<3;i++) {
                        int destination=right?2+i:-1-i;
                        if(folding)air(destination+direction,layer,z);
                        else if(material(destination,layer,z).isAir())air(destination,layer,z);
                    }
                    air(base,layer,z);
                    air(base+direction,layer,z);
                }
                return true;
            })) return false;
        }
        return true;
    }

    /** effects: stores fixed spine and equipment for folding; movable wings remain for real pushes. */
    public void prepareFolding() {
        CampSites.edit(()->{
            for(var cell:camp.plan().cells()) {
                var local=new Shelter.Pos(cell.offset().getX(),cell.offset().getY(),cell.offset().getZ()).rotate(-camp.turns());
                if(!shell.containsKey(cell.offset())||local.x()==0||local.x()==1) {
                    var p=camp.getBlockPos().offset(cell.offset());
                    if(level().getBlockEntity(p) instanceof net.minecraft.world.Container container)container.clearContent();
                    level().removeBlockEntity(p);
                    level().setBlock(p,Blocks.AIR.defaultBlockState(),18);
                }
            }
        });
    }

    /** effects: recognizes only the currently scheduled camp actuator, never an outside piston. */
    public static boolean actuator(Level level,BlockPos pos) {
        if(!(level instanceof ServerLevel server))return false;
        var anchor=CampSites.get(server).owner(pos);
        if(anchor==null||!(level.getBlockEntity(anchor) instanceof CampBlockEntity camp)
                ||!camp.mechanical()||camp.phase()==CampBlockEntity.Phase.ACTIVE)return false;
        int elapsed=camp.phase()==CampBlockEntity.Phase.FOLDING?Shelter.DURATION-camp.progress():camp.progress();
        var local=new Shelter.Pos(pos.getX()-anchor.getX(),pos.getY()-anchor.getY(),pos.getZ()-anchor.getZ()).rotate(-camp.turns());
        for(int wave=0;wave<START.length;wave++)if(elapsed>=START[wave]&&elapsed<=START[wave]+10) {
            boolean right=wave%2==0,folding=camp.phase()==CampBlockEntity.Phase.FOLDING;
            int y=folding?4-wave/2:wave/2;
            int base=folding?(right?5:-4):(right?0:1);
            if(local.x()==base&&local.y()==y&&local.z()>=0&&local.z()<8&&(y!=0||local.z()!=0))return true;
        }
        return false;
    }

    /** effects: lets the vanilla moving-block entity finish only within its transforming camp. */
    public static boolean moving(Level level,BlockPos pos) {
        if(!(level instanceof ServerLevel server))return false;
        var anchor=CampSites.get(server).owner(pos);
        return anchor!=null&&level.getBlockEntity(anchor) instanceof CampBlockEntity camp
                &&camp.mechanical()&&camp.phase()!=CampBlockEntity.Phase.ACTIVE;
    }
}
