/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.gametest;

import com.chunkworks.mobilecamp.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.slf4j.*;

import java.util.function.Consumer;

/**
 * Real-client gate for synchronized assembly, actual interaction packets and the module screen.
 * Captures require visual inspection; server assertions alone never claim visual correctness.
 */
@EventBusSubscriber(modid = "mobilecamp_gametest", value = Dist.CLIENT)
public final class CampBooth {
    private static final Logger LOG = LoggerFactory.getLogger("C.A.M.P. booth");
    private static final BlockPos CORE = new BlockPos(0, 100, 0);
    private static int tick;
    private static int rejectionTick;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("mobilecamp.booth")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        try {
            if (!rejectionGate(mc)) return;
            tick++;
            switch (tick) {
                case 20 ->
                        server(
                                mc,
                                p -> {
                                    var l = p.serverLevel();
                                    l.getGameRules()
                                            .getRule(GameRules.RULE_DOMOBSPAWNING)
                                            .set(false, l.getServer());
                                    l.setDayTime(1800);
                                    l.setWeatherParameters(6000, 0, false, false);
                                    for (int x = -14; x <= 17; x++)
                                        for (int z = -12; z <= 20; z++) {
                                            l.setBlock(
                                                    new BlockPos(x, 99, z),
                                                    ((x * x + z * z) % 7 == 0
                                                                    ? Blocks.COARSE_DIRT
                                                                    : Blocks.GRASS_BLOCK)
                                                            .defaultBlockState(),
                                                    3);
                                            for (int y = 100; y <= 110; y++)
                                                l.setBlock(
                                                        new BlockPos(x, y, z),
                                                        Blocks.AIR.defaultBlockState(),
                                                        3);
                                        }
                                    p.setGameMode(GameType.CREATIVE);
                                    p.getInventory().clearContent();
                                    var packed = PlacementGameTests.ladenCamp(l.registryAccess());
                                    var modules = new java.util.ArrayList<>(CampCargo.read(packed, l.registryAccess()));
                                    modules.set(2, new ItemStack(MobileCamp.CARTOGRAPHY.get()));
                                    CampCargo.write(packed, modules, l.registryAccess());
                                    p.setItemInHand(InteractionHand.MAIN_HAND, packed);
                                    view(p, .5, 100, -2.5, .5, 100, .5);
                                });
                case 70 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = true;
                }
                case 95 -> photo(mc, "01-packed-crate-in-hand");
                case 100 -> {
                    // Placement uses facing south and a genuine client-to-server item-use packet.
                    mc.player.setYRot(0);
                    mc.player.setXRot(25);
                    mc.gameMode.useItemOn(
                            mc.player,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(CORE.below()).add(0, .5, 0),
                                    Direction.UP,
                                    CORE.below(),
                                    false));
                }
                case 105 -> server(mc, p -> view(p, 12, 106, 15, .5, 101.8, 3.5));
                case 125 -> photo(mc, "02-deck-unfolding");
                case 165 -> photo(mc, "03-frame-rising");
                case 215 -> photo(mc, "04-walls-and-canopy");
                case 250 -> photo(mc, "05-equipment-unfolding");
                case 300 -> {
                    verdict(
                            mc.level.getBlockEntity(CORE) instanceof CampBlockEntity camp
                                    && camp.phase() == CampBlockEntity.Phase.ACTIVE,
                            "client sees fully deployed camp");
                    photo(mc, "06-camp-complete");
                }
                case 305 -> server(mc, p -> view(p, 6, 106, 1, 3, 105, 1));
                case 345 -> photo(mc, "13-roof-eave-joint");
                case 360 -> server(mc, p -> view(p, 2.5, 101, 11, .5, 102, 3));
                case 400 -> photo(mc, "07-workshop-front");
                case 420 -> server(mc, p -> {
                    view(p, -.5, 101, 7.5, -.5, 101.7, 5.5);
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    p.getInventory().setItem(9, net.minecraft.world.item.MapItem.create(p.serverLevel(), 0, 0, (byte) 0, true, false));
                    p.getInventory().setItem(10, new ItemStack(net.minecraft.world.item.Items.BOOK));
                    p.inventoryMenu.sendAllDataToRemote();
                });
                case 435 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(CORE.offset(-1,1,5)).add(0,.5,0),
                                Direction.UP, CORE.offset(-1,1,5), false));
                case 450 -> {
                    verdict(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CartographyTableScreen,
                            "attached cartography table opens real client screen");
                    mc.options.hideGui = false;
                    photo(mc, "16-cartography-station");
                }
                case 455 -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
                        3, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
                case 465 -> {
                    if (net.neoforged.fml.ModList.get().isLoaded("magicalmap"))
                        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
                                4, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
                }
                case 475 -> {
                    if (net.neoforged.fml.ModList.get().isLoaded("magicalmap")) {
                        var atlas = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse("magicalmap:atlas"));
                        verdict(mc.player.containerMenu.getSlot(2).getItem().is(atlas), "camp table offers real atlas result");
                        photo(mc, "17-camp-atlas-recipe");
                        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
                                2, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                    }
                }
                case 490 -> {
                    if (net.neoforged.fml.ModList.get().isLoaded("magicalmap"))
                        verdict(mc.player.containerMenu.getCarried().has(net.minecraft.core.component.DataComponents.CONTAINER),
                                "actual client takes the atlas from camp cartography");
                    mc.player.closeContainer();
                    mc.options.hideGui = true;
                }
                case 520 -> server(mc, p -> view(p, .5, 101, 2.8, 2.8, 101.6, 1));
                case 555 -> photo(mc, "08-bedroom-storage");
                case 560 -> server(mc, p -> view(p, .5, 101, 2.8, -2, 101.4, 1.5));
                case 595 -> photo(mc, "15-bedroll");
                case 615 -> server(mc, p -> {
                    view(p, 1.5, 100, -2.5, 1.5, 100.5, .5);
                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
                case 630 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(CORE.east()).add(0,0,-.5),
                                Direction.NORTH, CORE.east(), false));
                case 645 -> {
                    verdict(mc.player.containerMenu instanceof CampMenu, "ordinary deployed-control click opens modules");
                    mc.options.hideGui = false;
                    photo(mc, "14-deployed-module-control");
                }
                case 650 -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
                        1, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                case 660 -> {
                    verdict(mc.player.containerMenu.getCarried().is(MobileCamp.STORAGE.get()),
                            "real menu click removes storage module");
                    server(mc, p -> verdict(p.serverLevel().getBlockEntity(CORE.offset(2,1,1)) == null,
                            "module removal detaches actual chest"));
                }
                case 670 -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,
                        1, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                case 680 -> {
                    verdict(mc.player.containerMenu.getCarried().isEmpty(), "module reinserted through client packet");
                    server(mc, p -> {
                        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)
                                p.serverLevel().getBlockEntity(CORE.offset(2,1,1));
                        verdict(chest != null && chest.getItem(8).is(net.minecraft.world.item.Items.DIAMOND)
                                && chest.getItem(8).getCount()==17, "deployed module exchange preserves live cargo");
                    });
                    mc.player.closeContainer();
                    mc.options.hideGui = true;
                }
                case 730 ->
                        server(
                                mc,
                                p -> {
                                    view(p, .5, 100, -2.5, .5, 100.5, .5);
                                    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                });
                case 765 -> photo(mc, "09-collapse-button");
                case 780 ->
                        mc.gameMode.useItemOn(
                                mc.player,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(CORE).add(0, 0, -.5),
                                        Direction.NORTH,
                                        CORE,
                                        false));
                case 795 -> server(mc, p -> view(p, 12, 106, 15, .5, 101.8, 3.5));
                case 840 -> photo(mc, "10-collapsing");
                case 980 -> {
                    photo(mc, "11-packed-pickup");
                    server(
                            mc,
                            p -> {
                                verdict(
                                        p.serverLevel().getBlockEntity(CORE) == null,
                                        "real button packet folds camp");
                                var drops =
                                        p.serverLevel()
                                                .getEntitiesOfClass(
                                                        ItemEntity.class,
                                                        new AABB(CORE).inflate(12));
                                verdict(
                                        drops.size() == 1
                                                && drops.getFirst()
                                                        .getItem()
                                                        .is(MobileCamp.CAMP_ITEM.get()),
                                        "one packed pickup");
                                var drop = drops.getFirst();
                                p.setItemInHand(InteractionHand.MAIN_HAND, drop.getItem().copy());
                                drop.discard();
                                view(p, .5, 100, -2.5, .5, 100, .5);
                            });
                }
                case 1010 -> {
                    mc.options.hideGui = false;
                    mc.options.keyShift.setDown(true);
                }
                case 1030 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 1060 -> {
                    verdict(
                            mc.player.containerMenu instanceof CampMenu,
                            "sneak-use opens real synchronized module menu");
                    photo(mc, "12-module-bays");
                    mc.options.keyShift.setDown(false);
                }
                case 1090 -> {
                    mc.player.closeContainer();
                    LOG.info("camp booth: COMPLETE");
                    mc.stop();
                }
                default -> {}
            }
        } catch (Throwable error) {
            LOG.error("camp booth: FAIL", error);
            mc.stop();
        }
    }

    private static boolean rejectionGate(Minecraft mc) {
        // Survival is essential: creative prediction restores the item count and
        // concealed this regression in the original booth.
        if (rejectionTick >= 480) return true;
        int scenario = rejectionTick / 80;
        int step = rejectionTick++ % 80;
        var hand = scenario < 3 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (step == 0) server(mc, p -> {
            var level = p.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            level.setDayTime(1800);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            for (int x = -14; x <= 17; x++)
                for (int z = -12; z <= 20; z++) {
                    level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 100; y <= 110; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            level.setBlock(CORE.offset(3, 1, 5),
                    (scenario % 3 == 0 ? Blocks.STONE : scenario % 3 == 1 ? Blocks.WATER
                            : Blocks.AIR).defaultBlockState(), 3);
            if (scenario % 3 == 2)
                for (int depth = 1; depth <= 4; depth++)
                    level.setBlock(CORE.offset(-3, -depth, 0), Blocks.AIR.defaultBlockState(), 3);
            p.setItemInHand(hand, PlacementGameTests.ladenCamp(level.registryAccess()));
            view(p, .5, 100, -2.5, .5, 100, .5);
            p.inventoryMenu.sendAllDataToRemote();
        });
        if (step == 30) {
            mc.player.setYRot(0);
            var expected = mc.player.getItemInHand(hand).copy();
            verdict(expected.is(MobileCamp.CAMP_ITEM.get()), "survival rejection fixture synchronized");
            mc.gameMode.useItemOn(mc.player, hand,
                    new BlockHitResult(Vec3.atCenterOf(CORE.below()).add(0, .5, 0),
                            Direction.UP, CORE.below(), false));
            verdict(ItemStack.matches(expected, mc.player.getItemInHand(hand)),
                    "client prediction never consumes packed camp: " + scenario);
        }
        if (step == 60) {
            verdict(ItemStack.matches(mc.player.getItemInHand(hand),
                            PlacementGameTests.ladenCamp(mc.level.registryAccess())),
                    "rejected camp and nested cargo stay visible: " + scenario);
            verdict(!mc.level.getBlockState(CORE).is(MobileCamp.CAMP.get()),
                    "rejected site has no ghost core");
            server(mc, p -> {
                verdict(ItemStack.matches(p.getItemInHand(hand),
                                PlacementGameTests.ladenCamp(p.serverLevel().registryAccess())),
                        "rejection preserves authoritative camp and cargo");
                verdict(CampOwners.get(p.serverLevel()).active(p.getUUID()) == null,
                        "rejection leaves ownership free");
            });
        }
        return false;
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(
                () -> {
                    try {
                        action.accept(server.getPlayerList().getPlayer(id));
                    } catch (Throwable e) {
                        LOG.error("camp booth: FAIL", e);
                        mc.execute(mc::stop);
                    }
                });
    }

    private static void view(
            ServerPlayer p, double x, double y, double z, double tx, double ty, double tz) {
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.setDeltaMovement(Vec3.ZERO);
        double dx = tx - x, dy = ty - y - p.getEyeHeight(), dz = tz - z;
        p.teleportTo(
                p.serverLevel(),
                x,
                y,
                z,
                (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(
                mc.gameDirectory,
                name + ".png",
                mc.getMainRenderTarget(),
                m -> LOG.info("camp booth: {}", m.getString()));
    }

    private static void verdict(boolean ok, String reason) {
        if (ok) LOG.info("camp booth: PASS {}", reason);
        else throw new IllegalStateException(reason);
    }
}
