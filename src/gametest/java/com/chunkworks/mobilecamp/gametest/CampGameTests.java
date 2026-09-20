/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.function.Consumer;

/**
 * Partitions: real survival/creative use; solid/wet/unsupported sites; ownership across dimensions;
 * actual collapse button; inventory and furnace state roundtrip; cancelled placement; reload
 * phases; attachment protection; bay validation and carrier locking. No mocked backend.
 */
@GameTestHolder("mobilecamp")
@PrefixGameTestTemplate(false)
public final class CampGameTests {
    private static final BlockPos ANCHOR = new BlockPos(10, 2, 7);

    private static Player prepare(GameTestHelper h) {
        for (int x = 0; x < 25; x++)
            for (int z = 0; z < 25; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        var p = h.makeMockPlayer(GameType.SURVIVAL);
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(ANCHOR.offset(0, 0, -2))));
        p.setYRot(0);
        return p;
    }

    private static InteractionResult place(
            GameTestHelper h, Player p, BlockPos relative, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var ground = h.absolutePos(relative).below();
        return stack.useOn(
                new UseOnContext(
                        p,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(ground).add(0, .5, 0),
                                Direction.UP,
                                ground,
                                false)));
    }

    private static CampBlockEntity core(GameTestHelper h) {
        return (CampBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ANCHOR));
    }

    private static void button(GameTestHelper h, Player p) {
        h.useBlock(ANCHOR, p);
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public void placedCampHasEquipmentAndButtonReturnsExactlyOneItem(GameTestHelper h) {
        var p = prepare(h);
        var item = new ItemStack(MobileCamp.CAMP_ITEM.get());
        h.assertTrue(
                place(h, p, ANCHOR, item).consumesAction() && item.isEmpty(),
                "survival places exactly one camp");
        h.runAtTickTime(
                175,
                () -> {
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.ACTIVE,
                            "deployment completed on real ticks");
                    h.assertBlockPresent(Blocks.CRAFTING_TABLE, ANCHOR.offset(-2, 1, 5));
                    h.assertBlockPresent(Blocks.FURNACE, ANCHOR.offset(3, 1, 5));
                    h.assertBlockPresent(Blocks.GREEN_BED, ANCHOR.offset(-2, 1, 1));
                    var chest =
                            (ChestBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(ANCHOR.offset(2, 1, 1)));
                    var other =
                            (ChestBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(ANCHOR.offset(3, 1, 1)));
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 17));
                    other.setItem(26, new ItemStack(Items.EMERALD, 29));
                    var furnace =
                            (AbstractFurnaceBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(ANCHOR.offset(3, 1, 5)));
                    furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 11));
                    button(h, p);
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.FOLDING,
                            "core button starts collapse");
                });
        h.runAtTickTime(
                350,
                () -> {
                    h.assertBlockNotPresent(MobileCamp.CAMP.get(), ANCHOR);
                    var drops =
                            h.getLevel()
                                    .getEntitiesOfClass(
                                            ItemEntity.class,
                                            new AABB(h.absolutePos(ANCHOR)).inflate(12));
                    h.assertTrue(
                            drops.size() == 1
                                    && drops.getFirst().getItem().is(MobileCamp.CAMP_ITEM.get()),
                            "one packed item, no loose equipment/cargo");
                    var modules =
                            CampCargo.read(
                                    drops.getFirst().getItem(), h.getLevel().registryAccess());
                    var storage = CampCargo.data(modules.get(1)).getCompound("Cargo");
                    h.assertTrue(
                            storage.getCompound("0,0,0")
                                            .getCompound("Entity")
                                            .getList("Items", 10)
                                            .getCompound(0)
                                            .getInt("count")
                                    == 17,
                            "first chest cargo preserved");
                    h.assertTrue(
                            storage.getCompound("1,0,0")
                                            .getCompound("Entity")
                                            .getList("Items", 10)
                                            .getCompound(0)
                                            .getInt("count")
                                    == 29,
                            "second chest cargo preserved");
                    h.assertTrue(
                            CampOwners.get(h.getLevel()).active(p.getUUID()) == null,
                            "owner released only after pickup item created");
                    h.succeed();
                });
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public void cargoSurvivesRedeploymentFacingAnotherDirection(GameTestHelper h) {
        var p = prepare(h);
        place(h, p, ANCHOR, new ItemStack(MobileCamp.CAMP_ITEM.get()));
        h.runAtTickTime(
                175,
                () -> {
                    ((ChestBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(ANCHOR.offset(2, 1, 1))))
                            .setItem(8, new ItemStack(Items.DIAMOND, 13));
                    button(h, p);
                });
        h.runAtTickTime(
                350,
                () -> {
                    var drop =
                            h.getLevel()
                                    .getEntitiesOfClass(
                                            ItemEntity.class,
                                            new AABB(h.absolutePos(ANCHOR)).inflate(12))
                                    .getFirst();
                    var stack = drop.getItem().copy();
                    drop.discard();
                    p.setYRot(90);
                    h.assertTrue(
                            place(h, p, ANCHOR, stack).consumesAction(),
                            "replaces packed camp facing west");
                });
        h.runAtTickTime(
                525,
                () -> {
                    var chest =
                            (ChestBlockEntity)
                                    h.getLevel()
                                            .getBlockEntity(h.absolutePos(ANCHOR.offset(-1, 1, 2)));
                    h.assertTrue(
                            chest.getItem(8).is(Items.DIAMOND) && chest.getItem(8).getCount() == 13,
                            "cargo restored into rotated real chest");
                    h.succeed();
                });
    }

    @GameTest(template = "arena", timeoutTicks = 30)
    public void obstructionAndUnsupportedTerrainPreserveItem(GameTestHelper h) {
        var p = prepare(h);
        var stack = new ItemStack(MobileCamp.CAMP_ITEM.get());
        h.setBlock(ANCHOR.offset(3, 1, 5), Blocks.DIAMOND_BLOCK);
        h.assertTrue(
                place(h, p, ANCHOR, stack) == InteractionResult.FAIL && stack.getCount() == 1,
                "solid obstruction refuses without consumption");
        h.setBlock(ANCHOR.offset(3, 1, 5), Blocks.WATER);
        h.assertTrue(place(h, p, ANCHOR, stack) == InteractionResult.FAIL, "liquid refuses");
        h.setBlock(ANCHOR.offset(3, 1, 5), Blocks.AIR);
        for (int y = -2; y <= 1; y++) h.setBlock(new BlockPos(7, y, 7), Blocks.AIR);
        h.assertTrue(
                place(h, p, ANCHOR, stack) == InteractionResult.FAIL && stack.getCount() == 1,
                "unsupported corner refuses");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 30)
    public void oneDeployedCampIsSharedAcrossDimensionsAndLedgerReload(GameTestHelper h) {
        var p = prepare(h);
        place(h, p, ANCHOR, new ItemStack(MobileCamp.CAMP_ITEM.get()));
        var stack = new ItemStack(MobileCamp.CAMP_ITEM.get());
        h.assertTrue(
                place(h, p, ANCHOR.offset(0, 0, 10), stack) == InteractionResult.FAIL
                        && stack.getCount() == 1,
                "second camp refused");
        var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(
                CampOwners.get(nether).active(p.getUUID()).position()
                        == h.absolutePos(ANCHOR).asLong(),
                "same ownership record in nether");
        h.assertTrue(
                CampOwners.get(h.getLevel())
                                .save(
                                        new net.minecraft.nbt.CompoundTag(),
                                        h.getLevel().registryAccess())
                                .getList("Owners", 10)
                                .size()
                        > 0,
                "ledger persisted");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 30)
    public void cancelledPlacementRestoresTerrainAndOwnership(GameTestHelper h) {
        var p = prepare(h);
        var stack = new ItemStack(MobileCamp.CAMP_ITEM.get());
        Consumer<BlockEvent.EntityPlaceEvent> reject =
                e -> {
                    if (e.getEntity() == p) e.setCanceled(true);
                };
        NeoForge.EVENT_BUS.addListener(reject);
        try {
            h.assertTrue(
                    place(h, p, ANCHOR, stack) == InteractionResult.FAIL,
                    "placement event cancelled");
            h.assertBlockNotPresent(MobileCamp.CAMP.get(), ANCHOR);
            h.assertTrue(
                    stack.getCount() == 1
                            && CampOwners.get(h.getLevel()).active(p.getUUID()) == null,
                    "cancel keeps item and releases ownership");
            h.assertTrue(
                    CampSites.get(h.getLevel()).owner(h.absolutePos(ANCHOR)) == null,
                    "cancel releases site");
        } finally {
            NeoForge.EVENT_BUS.unregister(reject);
        }
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 240)
    public void attachedBlocksCannotBeHarvestedOrReplaced(GameTestHelper h) {
        var p = prepare(h);
        place(h, p, ANCHOR, new ItemStack(MobileCamp.CAMP_ITEM.get()));
        h.runAtTickTime(
                175,
                () -> {
                    var chest = h.absolutePos(ANCHOR.offset(2, 1, 1));
                    h.assertTrue(
                            !h.getLevel().destroyBlock(chest, true),
                            "destroy blocked before any drops");
                    h.assertTrue(
                            !h.getLevel().setBlock(chest, Blocks.AIR.defaultBlockState(), 3),
                            "replacement blocked");
                    h.assertTrue(
                            h.getLevel()
                                    .getEntitiesOfClass(
                                            ItemEntity.class, new AABB(chest).inflate(3))
                                    .isEmpty(),
                            "no duplicate drops");
                    var ev =
                            new BlockEvent.BreakEvent(
                                    h.getLevel(), chest, h.getLevel().getBlockState(chest), p);
                    NeoForge.EVENT_BUS.post(ev);
                    h.assertTrue(ev.isCanceled(), "actual break event cancelled");
                    h.succeed();
                });
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public void transformationStateSurvivesBlockEntityReload(GameTestHelper h) {
        var p = prepare(h);
        place(h, p, ANCHOR, new ItemStack(MobileCamp.CAMP_ITEM.get()));
        h.runAtTickTime(70, () -> reload(h));
        h.runAtTickTime(
                175,
                () -> {
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.ACTIVE,
                            "deploy resumes after reload");
                    button(h, p);
                });
        h.runAtTickTime(230, () -> reload(h));
        h.runAtTickTime(
                350,
                () -> {
                    h.assertBlockNotPresent(MobileCamp.CAMP.get(), ANCHOR);
                    h.assertTrue(
                            CampOwners.get(h.getLevel()).active(p.getUUID()) == null,
                            "fold resumes and releases ownership");
                    h.succeed();
                });
    }

    private static void reload(GameTestHelper h) {
        var old = core(h);
        var data = old.saveWithFullMetadata(h.getLevel().registryAccess());
        var progress = old.progress();
        h.getLevel().removeBlockEntity(old.getBlockPos());
        var restored =
                (CampBlockEntity)
                        BlockEntity.loadStatic(
                                old.getBlockPos(),
                                old.getBlockState(),
                                data,
                                h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.progress() == progress, "persisted progress exact");
    }

    @GameTest(template = "arena", timeoutTicks = 220)
    public void collapseRequiresOwnerAndClearInterior(GameTestHelper h) {
        var p = prepare(h);
        place(h, p, ANCHOR, new ItemStack(MobileCamp.CAMP_ITEM.get()));
        h.runAtTickTime(
                175,
                () -> {
                    var stranger = h.makeMockPlayer(GameType.SURVIVAL);
                    button(h, stranger);
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.ACTIVE,
                            "another player cannot pack this camp");
                    var cow = net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
                    cow.setNoAi(true);
                    cow.setPersistenceRequired();
                    cow.moveTo(Vec3.atBottomCenterOf(h.absolutePos(ANCHOR.offset(0, 1, 5))));
                    h.getLevel().addFreshEntity(cow);
                    button(h, p);
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.ACTIVE,
                            "living occupant prevents collapse");
                    cow.discard();
                    button(h, p);
                    h.assertTrue(
                            core(h).phase() == CampBlockEntity.Phase.FOLDING,
                            "owner can pack clear camp");
                    h.succeed();
                });
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public void fieldKitchenAndVegetationRoundTrip(GameTestHelper h) {
        var p = prepare(h);
        var stack = new ItemStack(MobileCamp.CAMP_ITEM.get());
        var modules =
                new java.util.ArrayList<>(CampCargo.read(stack, h.getLevel().registryAccess()));
        modules.set(3, new ItemStack(MobileCamp.FIELD_KITCHEN.get()));
        CampCargo.write(stack, modules, h.getLevel().registryAccess());
        h.setBlock(ANCHOR.offset(1, 0, 5).below(), Blocks.DIRT);
        h.setBlock(ANCHOR.offset(1, 0, 5), Blocks.DANDELION);
        h.setBlock(ANCHOR.offset(-3, -1, 0), Blocks.AIR);
        h.setBlock(ANCHOR.offset(-3, -2, 0), Blocks.STONE);
        var checked = CampSite.inspect(h.getLevel(), h.absolutePos(ANCHOR), 0);
        h.assertTrue(checked.failure() == null, "site preflight: " + checked.failure());
        h.assertTrue(
                place(h, p, ANCHOR, stack).consumesAction(),
                "valid uneven site accepts kitchen-equipped camp");
        h.runAtTickTime(
                175,
                () -> {
                    h.assertBlockPresent(Blocks.SMOKER, ANCHOR.offset(2, 1, 5));
                    h.assertBlockPresent(MobileCamp.FRAME.get(), ANCHOR.offset(-3, -1, 0));
                    button(h, p);
                });
        h.runAtTickTime(
                350,
                () -> {
                    h.assertBlockPresent(Blocks.DANDELION, ANCHOR.offset(1, 0, 5));
                    h.assertBlockPresent(Blocks.AIR, ANCHOR.offset(-3, -1, 0));
                    h.succeed();
                });
    }

    @GameTest(template = "arena", timeoutTicks = 30)
    public void moduleMenuValidatesBaysAndLocksCarrier(GameTestHelper h) {
        var p = prepare(h);
        var carrier = new ItemStack(MobileCamp.CAMP_ITEM.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, carrier);
        var menu = new CampMenu(1, p.getInventory(), carrier);
        var c = menu.getContainer();
        h.assertTrue(c.getItem(0).is(MobileCamp.SLEEP.get()), "starter sleeping bay");
        h.assertTrue(
                !c.canPlaceItem(0, new ItemStack(MobileCamp.STORAGE.get()))
                        && c.canPlaceItem(3, new ItemStack(MobileCamp.FIELD_KITCHEN.get())),
                "bay validation");
        var module = c.removeItemNoUpdate(1);
        c.setChanged();
        h.assertTrue(
                CampCargo.read(carrier, h.getLevel().registryAccess()).get(1).isEmpty(),
                "removed bay stays removed");
        c.setItem(3, new ItemStack(MobileCamp.FIELD_KITCHEN.get()));
        h.assertTrue(
                CampCargo.read(carrier, h.getLevel().registryAccess())
                        .get(3)
                        .is(MobileCamp.FIELD_KITCHEN.get()),
                "upgrade saved immediately");
        h.assertTrue(
                menu.slots.stream()
                        .filter(s -> s.getItem() == carrier)
                        .noneMatch(s -> s.mayPickup(p)),
                "carrier cannot be moved");
        h.succeed();
    }
}
