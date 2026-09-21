/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Partitions: recipe and four rotations; existing deployed upgrade and wrong bays;
 * real vanilla clone and optional actual Magical Map create/bind/extract; packed
 * serialization and redeployment. Uses registered recipes, menus and live blocks. */
@GameTestHolder("mobilecamp")
@PrefixGameTestTemplate(false)
public final class CartographyGameTests {
    private static final BlockPos CORE = new BlockPos(10, 2, 7);
    private static final BlockPos CRAFT = CORE.offset(-2, 1, 5);
    private static final BlockPos TABLE = CORE.offset(-1, 1, 5);

    @GameTest(template = "arena")
    public void recipeAndEveryRotationFitTheWorkshop(GameTestHelper h) {
        var iron = new ItemStack(Items.IRON_INGOT);
        var input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, iron, ItemStack.EMPTY,
                iron, new ItemStack(Items.CARTOGRAPHY_TABLE), iron,
                ItemStack.EMPTY, new ItemStack(Items.LEATHER), ItemStack.EMPTY));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        var result = recipe.value().assemble(input, h.getLevel().registryAccess());
        h.assertTrue(result.is(MobileCamp.CARTOGRAPHY.get()) && result.getCount() == 1,
                "registered crafting recipe produces one cartography module");
        var modules = new ArrayList<>(CampCargo.read(new ItemStack(MobileCamp.CAMP_ITEM.get()), h.getLevel().registryAccess()));
        modules.set(2, result);
        for (int turns = 0; turns < 4; turns++) {
            var cells = new CampPlan(modules, turns).cells();
            h.assertTrue(cells.stream().filter(c -> c.state().is(Blocks.CRAFTING_TABLE)).count() == 1
                    && cells.stream().filter(c -> c.state().is(Blocks.CARTOGRAPHY_TABLE)).count() == 1,
                    "both real stations fit without overlap at rotation " + turns);
        }
        h.succeed();
    }

    @GameTest(template = "arena", batch = "camp-cartography", timeoutTicks = 600)
    public void deployedUpgradeWorksAndSurvivesPacking(GameTestHelper h) {
        var p = CampFeatureGameTests.place(h);
        h.runAtTickTime(175, () -> {
            use(h, p, CORE.east());
            h.assertTrue(p.containerMenu instanceof CampMenu, "blue control opens existing camp");
            var menu = (CampMenu) p.containerMenu;
            var module = new ItemStack(MobileCamp.CARTOGRAPHY.get());
            for (int bay = 0; bay < 4; bay++)
                h.assertTrue(menu.getSlot(bay).mayPlace(module) == (bay == 2), "only workshop bay accepts cartography");
            p.getInventory().setItem(9, module);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, p);
            menu.clicked(9, 0, ClickType.PICKUP, p);
            menu.clicked(2, 0, ClickType.PICKUP, p);
            h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(2).getItem().is(MobileCamp.CARTOGRAPHY.get()),
                    "real slots replace crafting module once");
            p.closeContainer();
            h.assertBlockPresent(Blocks.CRAFTING_TABLE, CRAFT);
            h.assertBlockPresent(Blocks.CARTOGRAPHY_TABLE, TABLE);
            var near = h.absolutePos(TABLE.south());
            p.moveTo(near.getX()+.5, near.getY(), near.getZ()+.5, 180, 0);
            use(h, p, CRAFT);
            h.assertTrue(p.containerMenu instanceof CraftingMenu, "crafting table remains usable");
            p.closeContainer();
            use(h, p, TABLE);
            h.assertTrue(p.containerMenu instanceof CartographyTableMenu, "attached table opens vanilla cartography");
            var table = (CartographyTableMenu) p.containerMenu;
            var original = MapItem.create(h.getLevel(), near.getX(), near.getZ(), (byte) 0, true, false);
            var id = original.get(DataComponents.MAP_ID);
            table.getSlot(0).set(original.copy());
            table.getSlot(1).set(new ItemStack(Items.MAP));
            table.clicked(2, 0, ClickType.PICKUP, p);
            h.assertTrue(table.getCarried().getCount() == 2 && id.equals(table.getCarried().get(DataComponents.MAP_ID)),
                    "vanilla map cloning works on the attached station");
            table.setCarried(ItemStack.EMPTY);
            if (ModList.get().isLoaded("magicalmap")) {
                table.getSlot(0).set(original.copy());
                table.getSlot(1).set(new ItemStack(Items.BOOK));
                table.clicked(2, 0, ClickType.PICKUP, p);
                var atlas = table.getCarried().copy();
                var atlasItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("magicalmap:atlas"));
                h.assertTrue(atlas.is(atlasItem) && atlas.get(DataComponents.CONTAINER).stream().count() == 1,
                        "actual Magical Map jar creates atlas through attached menu");
                table.setCarried(ItemStack.EMPTY);
                var next = MapItem.create(h.getLevel(), near.getX()+128, near.getZ(), (byte) 0, true, false);
                table.getSlot(0).set(atlas);
                table.getSlot(1).set(next.copy());
                table.clicked(2, 0, ClickType.PICKUP, p);
                atlas = table.getCarried().copy();
                h.assertTrue(atlas.get(DataComponents.CONTAINER).stream().count() == 2, "actual atlas binds another sheet");
                table.setCarried(ItemStack.EMPTY);
                table.getSlot(0).set(atlas);
                table.getSlot(1).set(new ItemStack(Items.SHEARS));
                table.clicked(2, 0, ClickType.PICKUP, p);
                h.assertTrue(ItemStack.isSameItemSameComponents(table.getCarried(), next)
                        && table.getSlot(0).getItem().get(DataComponents.CONTAINER).stream().count() == 1,
                        "actual atlas returns exact sheet and retains remainder");
                System.out.println("CARTOGRAPHY INTEGRATION: actual Magical Map create/bind/extract passed");
            } else System.out.println("CARTOGRAPHY INTEGRATION NOT RUN: optional Magical Map jar absent");
            p.closeContainer();
            // Keep station inputs and player drops outside the folding volume.
            near = h.absolutePos(CORE.north(2));
            p.moveTo(near.getX()+.5, near.getY(), near.getZ()+.5, 0, 0);
            use(h, p, CORE);
            h.assertTrue(((CampBlockEntity) h.getBlockEntity(CORE)).phase() == CampBlockEntity.Phase.FOLDING,
                    "real collapse control starts packing upgraded camp");
        });
        h.runAtTickTime(350, () -> {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(CORE)).inflate(12),
                    e -> e.getItem().is(MobileCamp.CAMP_ITEM.get()));
            h.assertTrue(drops.size() == 1, "exactly one packed camp");
            var drop = drops.getFirst();
            var registries = h.getLevel().registryAccess();
            var saved = ItemStack.parse(registries, drop.getItem().save(registries)).orElseThrow();
            h.assertTrue(CampCargo.read(saved, registries).get(2).is(MobileCamp.CARTOGRAPHY.get()),
                    "cartography selection survives actual item persistence");
            drop.discard();
            p.setItemInHand(InteractionHand.MAIN_HAND, saved);
            use(h, p, CORE.below());
            h.assertBlockPresent(MobileCamp.CAMP.get(), CORE);
        });
        h.runAtTickTime(525, () -> {
            try {
                h.assertBlockPresent(Blocks.CRAFTING_TABLE, CRAFT);
                h.assertBlockPresent(Blocks.CARTOGRAPHY_TABLE, TABLE);
                h.succeed();
            } finally { p.closeContainer(); PlacementPlayers.remove(p); }
        });
    }

    private static void use(GameTestHelper h, ServerPlayer p, BlockPos relative) {
        var pos = h.absolutePos(relative);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false));
    }
}
