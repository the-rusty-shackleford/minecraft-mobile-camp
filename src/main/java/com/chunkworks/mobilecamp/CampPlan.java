/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;

import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

import java.util.*;

/**
 * Immutable placed geometry. AF: parts map local offsets to placed states and module cargo keys.
 * RI: unique positions; module parts do not collide with the shell.
 */
public final class CampPlan {
    public record Cell(
            BlockPos offset, BlockState state, Shelter.Kind mechanism, int bay, String cargoKey) {}

    private final List<Cell> cells;

    /** requires: four bay stacks; effects: builds rotated geometry; throws: colliding parts. */
    public CampPlan(List<ItemStack> modules, int turns) {
        var list = new ArrayList<Cell>();
        var rotation = Rotation.values()[Math.floorMod(turns, 4)];
        for (var p : Shelter.frame()) {
            BlockState state =
                    switch (p.kind()) {
                        case DECK -> MobileCamp.DECK.get().defaultBlockState();
                        case POST -> MobileCamp.FRAME.get().defaultBlockState();
                        case WALL -> MobileCamp.PANEL.get().defaultBlockState();
                        case WINDOW ->
                                Blocks.GLASS_PANE
                                        .defaultBlockState()
                                        .setValue(BlockStateProperties.NORTH, true)
                                        .setValue(BlockStateProperties.SOUTH, true);
                        case CANVAS ->
                                MobileCamp.CANVAS
                                        .get()
                                        .defaultBlockState()
                                        .setValue(
                                                CanvasBlock.RISE,
                                                p.pos().x() <= 0
                                                        ? p.pos().x() + 3
                                                        : 4 - p.pos().x())
                                        .setValue(
                                                CanvasBlock.FACING,
                                                p.pos().x() <= 0 ? Direction.EAST : Direction.WEST)
                                        .setValue(
                                                CanvasBlock.GABLE,
                                                p.pos().z() == 0 || p.pos().z() == 3);
                        case LANTERN ->
                                Blocks.LANTERN
                                        .defaultBlockState()
                                        .setValue(LanternBlock.HANGING, true);
                    };
            if (p.kind() == Shelter.Kind.WINDOW && p.pos().z() == 0)
                state =
                        state.setValue(BlockStateProperties.NORTH, false)
                                .setValue(BlockStateProperties.SOUTH, false)
                                .setValue(BlockStateProperties.EAST, true)
                                .setValue(BlockStateProperties.WEST, true);
            list.add(
                    new Cell(pos(p.pos().rotate(turns)), state.rotate(rotation), p.kind(), -1, ""));
        }
        for (int x = 0; x <= 1; x++)
            for (int y = 1; y <= 2; y++) {
                var state =
                        Blocks.SPRUCE_DOOR
                                .defaultBlockState()
                                .setValue(DoorBlock.FACING, Direction.SOUTH)
                                .setValue(
                                        DoorBlock.HALF,
                                        y == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
                                .setValue(
                                        DoorBlock.HINGE,
                                        x == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
                list.add(
                        new Cell(
                                pos(new Shelter.Pos(x, y, 3).rotate(turns)),
                                state.rotate(rotation),
                                Shelter.Kind.WALL,
                                -1,
                                ""));
            }
        for (int bay = 0; bay < 4; bay++)
            if (modules.get(bay).getItem() instanceof ModuleItem module) {
                var origin = Shelter.bays().get(bay);
                for (var part : module.parts()) {
                    var p = part.pos();
                    var offset =
                            new Shelter.Pos(
                                            origin.x() + p.x(),
                                            origin.y() + p.y(),
                                            origin.z() + p.z())
                                    .rotate(turns);
                    list.add(
                            new Cell(
                                    pos(offset),
                                    part.state().rotate(rotation),
                                    Shelter.Kind.LANTERN,
                                    bay,
                                    p.x() + "," + p.y() + "," + p.z()));
                }
            }
        var seen = new HashSet<BlockPos>();
        for (var cell : list)
            if (!seen.add(cell.offset()))
                throw new IllegalArgumentException("Overlapping camp part " + cell.offset());
        cells = List.copyOf(list);
    }

    public static BlockPos pos(Shelter.Pos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    /** effects: returns immutable parts; throws: none. */
    public List<Cell> cells() {
        return cells;
    }

    /** effects: returns the complete reserved interior including the core, in world coordinates. */
    public static List<BlockPos> volume(BlockPos core, int turns) {
        var positions = new ArrayList<BlockPos>();
        for (int x = -3; x <= 4; x++)
            for (int y = 0; y < Shelter.HEIGHT; y++)
                for (int z = 0; z <= 7; z++)
                    positions.add(core.offset(pos(new Shelter.Pos(x, y, z).rotate(turns))));
        return positions;
    }
}
