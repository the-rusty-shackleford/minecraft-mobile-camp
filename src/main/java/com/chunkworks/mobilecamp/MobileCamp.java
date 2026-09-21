/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;

import java.util.List;

/** Mod composition root. Registers the camp and independently replaceable equipment items. */
@Mod(MobileCamp.ID)
public final class MobileCamp {
    public static final String ID = "mobilecamp";
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<Block, CampBlock> CAMP =
            BLOCKS.register(
                    "camp",
                    () ->
                            new CampBlock(
                                    BlockBehaviour.Properties.of()
                                            .strength(2, 1200)
                                            .noOcclusion()
                                            .sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, CampControlBlock> CONTROL = BLOCKS.register("module_control",
            () -> new CampControlBlock(BlockBehaviour.Properties.of().strength(2, 1200).noOcclusion().sound(SoundType.METAL)));
    public static final DeferredHolder<Block, BedrollBlock> BEDROLL = BLOCKS.register("bedroll",
            () -> new BedrollBlock(BlockBehaviour.Properties.of().strength(2, 1200).noOcclusion().sound(SoundType.WOOL)));
    public static final DeferredHolder<Block, CanvasBlock> CANVAS =
            BLOCKS.register(
                    "canvas",
                    () ->
                            new CanvasBlock(
                                    BlockBehaviour.Properties.of()
                                            .strength(2, 1200)
                                            .noOcclusion()
                                            .sound(SoundType.WOOL)));
    public static final DeferredHolder<Block, Block> FRAME =
            BLOCKS.register(
                    "frame",
                    () ->
                            new Block(
                                    BlockBehaviour.Properties.of()
                                            .strength(2, 1200)
                                            .noOcclusion()
                                            .sound(SoundType.COPPER)) {
                                @Override
                                protected net.minecraft.world.phys.shapes.VoxelShape getShape(
                                        net.minecraft.world.level.block.state.BlockState s,
                                        net.minecraft.world.level.BlockGetter l,
                                        net.minecraft.core.BlockPos p,
                                        net.minecraft.world.phys.shapes.CollisionContext c) {
                                    return Block.box(4, 0, 4, 12, 16, 12);
                                }
                            });
    public static final DeferredHolder<Block, Block> PANEL =
            BLOCKS.register(
                    "panel",
                    () ->
                            new Block(
                                    BlockBehaviour.Properties.of()
                                            .strength(2, 1200)
                                            .sound(SoundType.WOOD)));
    public static final DeferredHolder<Block, Block> DECK =
            BLOCKS.register(
                    "deck",
                    () ->
                            new Block(
                                    BlockBehaviour.Properties.of()
                                            .strength(2, 1200)
                                            .noOcclusion()
                                            .sound(SoundType.WOOD)) {
                                @Override
                                protected net.minecraft.world.phys.shapes.VoxelShape getShape(
                                        net.minecraft.world.level.block.state.BlockState s,
                                        net.minecraft.world.level.BlockGetter l,
                                        net.minecraft.core.BlockPos p,
                                        net.minecraft.world.phys.shapes.CollisionContext c) {
                                    return Block.box(0, 0, 0, 16, 16, 16);
                                }
                            });
    public static final DeferredHolder<Block, Block> MOUNT = BLOCKS.register("module_mount",
            () -> new Block(BlockBehaviour.Properties.of().strength(2,1200).sound(SoundType.METAL)));
    public static final DeferredHolder<Item, BlockItem> DECK_ITEM = ITEMS.register("deck",()->new BlockItem(DECK.get(),new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> FRAME_ITEM = ITEMS.register("frame",()->new BlockItem(FRAME.get(),new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> PANEL_ITEM = ITEMS.register("panel",()->new BlockItem(PANEL.get(),new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> CANVAS_ITEM = ITEMS.register("canvas",()->new BlockItem(CANVAS.get(),new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> MOUNT_ITEM = ITEMS.register("module_mount",()->new BlockItem(MOUNT.get(),new Item.Properties()));
    public static final DeferredHolder<Item, BedItem> BEDROLL_ITEM = ITEMS.register("bedroll",()->new BedItem(BEDROLL.get(),new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, CampItem> CAMP_ITEM =
            ITEMS.register("camp", () -> new CampItem(CAMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampBlockEntity>> CORE =
            ENTITIES.register(
                    "camp",
                    () -> BlockEntityType.Builder.of(CampBlockEntity::new, CAMP.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CampMenu>> MENU =
            MENUS.register(
                    "modules", () -> new MenuType<>(CampMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<Item, ModuleItem> SLEEP =
            ITEMS.register(
                    "sleeping_module",
                    () ->
                            new ModuleItem(
                                    0,
                                    List.of(
                                            part(
                                                    0,
                                                    0,
                                                    0,
                                                    BEDROLL.get()
                                                            .defaultBlockState()
                                                            .setValue(
                                                                    BedBlock.FACING,
                                                                    Direction.NORTH)
                                                            .setValue(BedBlock.PART, BedPart.HEAD)),
                                            part(
                                                    0,
                                                    0,
                                                    1,
                                                    BEDROLL.get()
                                                            .defaultBlockState()
                                                            .setValue(
                                                                    BedBlock.FACING,
                                                                    Direction.NORTH)
                                                            .setValue(
                                                                    BedBlock.PART,
                                                                    BedPart.FOOT)))));
    public static final DeferredHolder<Item, ModuleItem> STORAGE =
            ITEMS.register(
                    "storage_module",
                    () ->
                            new ModuleItem(
                                    1,
                                    List.of(
                                            part(
                                                    0,
                                                    0,
                                                    0,
                                                    Blocks.CHEST
                                                            .defaultBlockState()
                                                            .setValue(
                                                                    ChestBlock.FACING,
                                                                    Direction.SOUTH)
                                                            .setValue(
                                                                    ChestBlock.TYPE,
                                                                    ChestType.RIGHT)),
                                            part(
                                                    1,
                                                    0,
                                                    0,
                                                    Blocks.CHEST
                                                            .defaultBlockState()
                                                            .setValue(
                                                                    ChestBlock.FACING,
                                                                    Direction.SOUTH)
                                                            .setValue(
                                                                    ChestBlock.TYPE,
                                                                    ChestType.LEFT)))));
    public static final DeferredHolder<Item, ModuleItem> WORK =
            ITEMS.register(
                    "crafting_module",
                    () ->
                            new ModuleItem(
                                    2,
                                    List.of(
                                            part(
                                                    0,
                                                    0,
                                                    0,
                                                    Blocks.CRAFTING_TABLE.defaultBlockState()))));
    public static final DeferredHolder<Item, ModuleItem> CARTOGRAPHY =
            ITEMS.register(
                    "cartography_module",
                    () -> new ModuleItem(2, List.of(
                            part(0, 0, 0, Blocks.CRAFTING_TABLE.defaultBlockState()),
                            part(1, 0, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState()))));
    public static final DeferredHolder<Item, ModuleItem> COOK =
            ITEMS.register("cooking_module", () -> new ModuleItem(3, cooking(false)));
    public static final DeferredHolder<Item, ModuleItem> FIELD_KITCHEN =
            ITEMS.register("field_kitchen_module", () -> new ModuleItem(3, cooking(true)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register(
                    "camp",
                    () ->
                            CreativeModeTab.builder()
                                    .title(Component.translatable("itemGroup.mobilecamp"))
                                    .icon(() -> CAMP_ITEM.get().getDefaultInstance())
                                    .displayItems(
                                            (p, out) -> {
                                                out.accept(CAMP_ITEM.get());
                                                out.accept(SLEEP.get());
                                                out.accept(STORAGE.get());
                                                out.accept(WORK.get());
                                                out.accept(CARTOGRAPHY.get());
                                                out.accept(COOK.get());
                                                out.accept(FIELD_KITCHEN.get());
                                                out.accept(DECK_ITEM.get());out.accept(FRAME_ITEM.get());
                                                out.accept(PANEL_ITEM.get());out.accept(CANVAS_ITEM.get());
                                                out.accept(MOUNT_ITEM.get());out.accept(BEDROLL_ITEM.get());
                                            })
                                    .build());

    private static ModuleItem.Part part(
            int x, int y, int z, net.minecraft.world.level.block.state.BlockState s) {
        return new ModuleItem.Part(new Shelter.Pos(x, y, z), s);
    }

    private static List<ModuleItem.Part> cooking(boolean upgrade) {
        var parts = new java.util.ArrayList<ModuleItem.Part>();
        parts.add(
                part(
                        1,
                        0,
                        0,
                        Blocks.FURNACE
                                .defaultBlockState()
                                .setValue(FurnaceBlock.FACING, Direction.WEST)));
        parts.add(
                part(
                        1,
                        0,
                        1,
                        Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)));
        if (upgrade)
            parts.add(
                    part(
                            0,
                            0,
                            0,
                            Blocks.SMOKER
                                    .defaultBlockState()
                                    .setValue(SmokerBlock.FACING, Direction.SOUTH)));
        return List.copyOf(parts);
    }

    /** requires: NeoForge mod bus; effects: registers content; throws: registry failures. */
    public MobileCamp(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
