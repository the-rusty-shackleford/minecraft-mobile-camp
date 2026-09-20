/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Persistent camp transaction. AF: carrier holds four module stacks, site holds original terrain,
 * phase/progress describe deployment or retraction. RI: only ACTIVE owns placed equipment; FOLDING
 * holds its captured contents in carrier. Site and player reservations last until final pickup. All
 * world mutations occur on the server thread in a scoped reservation bypass.
 */
public final class CampBlockEntity extends BlockEntity {
    public enum Phase {
        DEPLOYING,
        ACTIVE,
        FOLDING
    }

    private Phase phase = Phase.DEPLOYING;
    private int progress;
    private boolean initialized, paused, mechanical;
    private UUID owner;
    private ItemStack carrier = ItemStack.EMPTY;
    private List<ItemStack> modules =
            List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    private List<BlockPos> supports = List.of();
    private final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
    private CampPlan plan;
    private CampMenu editor;
    private CampMechanism mechanism;
    private AABB siteBounds;

    public CampBlockEntity(BlockPos pos, BlockState state) {
        super(MobileCamp.CORE.get(), pos, state);
    }

    /** effects: distinguishes real-piston camps from legacy in-flight saves. */
    public boolean mechanical() { return mechanical; }

    private CampMechanism mechanism() {
        if (mechanism == null) mechanism = new CampMechanism(this);
        return mechanism;
    }

    public Phase phase() {
        return phase;
    }

    public int progress() {
        return progress;
    }

    public boolean paused() {
        return paused;
    }

    public int turns() {
        return getBlockState().getValue(CampBlock.FACING).get2DDataValue();
    }

    public CampPlan plan() {
        if (plan == null) plan = new CampPlan(modules, turns());
        return plan;
    }

    public List<BlockPos> supports() {
        return supports;
    }

    /**
     * requires: NeoForge is rolling back cancelled item placement; effects: releases reservations,
     * while NeoForge restores world snapshots and the unspent item itself.
     */
    public void rollbackPlacement() {
        if (initialized && level instanceof ServerLevel server && server.restoringBlockSnapshots) {
            CampSites.get(server).release(worldPosition);
            CampOwners.get(server).release(owner, server, worldPosition);
        }
    }

    /**
     * requires: freshly placed core on server, valid preflight and no other camp for player;
     * effects: reserves site and starts mechanical deployment.
     */
    public void begin(ItemStack item, Player player) {
        var server = (ServerLevel) level;
        var site = CampSite.inspect(server, worldPosition, turns(), true);
        if (site.failure() != null)
            throw new IllegalStateException("Placement changed during camp transaction");
        carrier = item.copyWithCount(1);
        modules = CampCargo.read(carrier, server.registryAccess());
        CampCargo.write(carrier, modules, server.registryAccess());
        plan = new CampPlan(modules, turns());
        owner = player.getUUID();
        supports = site.supports();
        mechanical = true;
        var volume = new ArrayList<>(CampMechanism.volume(worldPosition, turns()));
        volume.addAll(supports);
        for (var pos : volume)
            if (!pos.equals(worldPosition)) originals.put(pos, server.getBlockState(pos));
        originals.put(worldPosition, CampItem.originalAtPlacement());
        CampOwners.get(server).claim(owner, server, worldPosition);
        CampSites.get(server).claim(worldPosition, volume);
        initialized = true;
        progress = 0;
        phase = Phase.DEPLOYING;
        CampSites.edit(
                () -> {
                    for (var pos : originals.keySet())
                        if (!pos.equals(worldPosition))
                            server.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                });
        sync();
        sound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.8f);
    }

    /**
     * requires: server player; effects: starts retraction only for owner/operator, clear loaded
     * site. Otherwise explains refusal and changes no cargo.
     */
    public void requestPack(Player player) {
        if (!initialized) return;
        if (!player.getUUID().equals(owner) && !player.hasPermissions(2)) {
            message(player, "not_owner");
            return;
        }
        if (phase != Phase.ACTIVE) {
            message(player, "transforming");
            return;
        }
        if (!loaded()) {
            message(player, "wait_loaded");
            return;
        }
        if (occupied()) {
            message(player, "occupied");
            return;
        }
        if (editor != null) { message(player, "editing"); return; }
        captureCargo();
        phase = Phase.FOLDING;
        progress = Shelter.DURATION;
        if (mechanical) mechanism().prepareFolding();
        else CampSites.edit(
                () -> {
                    for (var cell : plan().cells()) {
                        var pos = worldPosition.offset(cell.offset());
                        var entity = level.getBlockEntity(pos);
                        if (entity instanceof Container container) container.clearContent();
                        // Remove the BE first: campfire contents and non-Container mod storage must
                        // not drop.
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                    }
                    for (var pos : supports)
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                });
        sync();
        sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.7f);
    }

    /** requires: server player; effects: opens one deployed-module editor when safe. */
    public void openModules(Player player) {
        if (!canEditModules(player)) return;
        if (editor != null) { message(player, "editing"); return; }
        captureCargo();
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> {
            editor = new CampMenu(id, inventory, this);
            return editor;
        }, Component.translatable("container.mobilecamp.modules")));
    }

    /** effects: validates authority, range, loaded terrain and clear interior. */
    boolean canEditModules(Player player) {
        if (!initialized || isRemoved() || phase != Phase.ACTIVE) { message(player,"transforming"); return false; }
        if (!player.getUUID().equals(owner) && !player.hasPermissions(2)) { message(player,"not_owner"); return false; }
        if (player.level() != level || player.distanceToSqr(worldPosition.getCenter()) > 64) return false;
        if (!loaded()) { message(player,"wait_loaded"); return false; }
        if (occupied()) { message(player,"occupied"); return false; }
        return true;
    }

    /** requires: authorized server menu click; effects: snapshots live station cargo. */
    List<ItemStack> editingModules() {
        captureCargo();
        return modules.stream().map(ItemStack::copy).toList();
    }

    /** requires: validated menu click, four valid bay stacks; effects: atomically replaces
     * stations, preserving their cargo without loose drops or shell changes. */
    void applyModules(List<ItemStack> replacements) {
        var next = replacements.stream().map(ItemStack::copy).toList();
        boolean changed = false;
        for (int i=0; i<4; i++) changed |= !ItemStack.matches(next.get(i),modules.get(i));
        if (!changed) return;
        var nextPlan = new CampPlan(next,turns());
        CampSites.edit(() -> {
            for (var cell : plan().cells()) if (cell.bay() >= 0) {
                var pos = worldPosition.offset(cell.offset());
                if (level.getBlockEntity(pos) instanceof Container container) container.clearContent();
                level.removeBlockEntity(pos);
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);
            }
            modules = next;
            CampCargo.write(carrier, modules, level.registryAccess());
            plan = nextPlan;
            for (var cell : plan.cells()) if (cell.bay() >= 0) placeCell(cell);
        });
        for (var cell : plan.cells()) if (cell.bay() >= 0) {
            var pos = worldPosition.offset(cell.offset());
            level.updateNeighborsAt(pos,level.getBlockState(pos).getBlock());
        }
        sync();
    }

    /** effects: releases only this menu's editor lease. */
    void closeModules(CampMenu menu) { if (editor == menu) editor = null; }
    private void captureCargo() {
        var cargo = new CompoundTag[4];
        for (int i = 0; i < 4; i++) cargo[i] = new CompoundTag();
        for (var cell : plan().cells())
            if (cell.bay() >= 0) {
                var pos = worldPosition.offset(cell.offset());
                var entry = new CompoundTag();
                var entity = level.getBlockEntity(pos);
                if (entity != null)
                    entry.put("Entity", entity.saveWithoutMetadata(level.registryAccess()));
                var state =
                        level.getBlockState(pos)
                                .rotate(Rotation.values()[Math.floorMod(-turns(), 4)]);
                if (state.hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                                .OCCUPIED))
                    state =
                            state.setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.OCCUPIED,
                                    false);
                if (state.hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties
                                .WATERLOGGED))
                    state =
                            state.setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.WATERLOGGED,
                                    false);
                entry.put("State", NbtUtils.writeBlockState(state));
                cargo[cell.bay()].put(cell.cargoKey(), entry);
            }
        for (int i = 0; i < 4; i++)
            if (!modules.get(i).isEmpty()) {
                var data = CampCargo.data(modules.get(i));
                data.put("Cargo", cargo[i]);
                CampCargo.data(modules.get(i), data);
            }
        CampCargo.write(carrier, modules, level.registryAccess());
    }

    private void placeCell(CampPlan.Cell cell) {
                        var pos = worldPosition.offset(cell.offset());
                        var state = cell.state();
                        var entry =
                                cell.bay() < 0
                                        ? new CompoundTag()
                                        : CampCargo.data(modules.get(cell.bay()))
                                                .getCompound("Cargo")
                                                .getCompound(cell.cargoKey());
                        if (entry.contains("State")) {
                            var saved =
                                    NbtUtils.readBlockState(
                                                    level.holderLookup(Registries.BLOCK),
                                                    entry.getCompound("State"))
                                            .rotate(Rotation.values()[turns()]);
                            if (saved.getBlock() == state.getBlock()) state = saved;
                        }
                        level.setBlock(pos, state, 18);
                        if (level.getBlockEntity(pos) instanceof BlockEntity entity
                                && entry.contains("Entity")) {
                            entity.loadWithComponents(
                                    entry.getCompound("Entity"), level.registryAccess());
                            entity.setChanged();
                        }
    }

    private void deploy() {
        CampSites.edit(
                () -> {
                    for (var pos : supports)
                        level.setBlock(pos, MobileCamp.DECK.get().defaultBlockState(), 18);
                    for (var cell : plan().cells()) {
                        placeCell(cell);
                    }
                });
        phase = Phase.ACTIVE;
        progress = Shelter.DURATION;
        sync();
        // Paired beds/chests/doors are complete before neighbor notification.
        for (var cell : plan().cells()) {
            var pos = worldPosition.offset(cell.offset());
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
        sound(SoundEvents.CHEST_LOCKED, 0.8f);
    }

    private void finishPacking() {
        var server = (ServerLevel) level;
        // Keep reservations until every original cell is restored; none of this requires a chunk
        // load.
        CampSites.edit(
                () -> {
                    for (var entry : originals.entrySet())
                        if (!entry.getKey().equals(worldPosition))
                            server.setBlock(entry.getKey(), entry.getValue(), 18);
                    server.setBlock(
                            worldPosition,
                            originals.getOrDefault(worldPosition, Blocks.AIR.defaultBlockState()),
                            18);
                });
        CampSites.get(server).release(worldPosition);
        CampOwners.get(server).release(owner, server, worldPosition);
        var item =
                new ItemEntity(
                        server,
                        worldPosition.getX() + .5,
                        worldPosition.getY() + 1,
                        worldPosition.getZ() + .5,
                        carrier.copy());
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    private boolean loaded() {
        for (var pos : originals.keySet()) if (!level.hasChunkAt(pos)) return false;
        return true;
    }

    public AABB bounds() {
        if (siteBounds != null) return siteBounds;
        var positions = mechanical ? CampMechanism.volume(worldPosition, turns()) : CampPlan.volume(worldPosition, turns());
        var a = positions.getFirst();
        var b = positions.getLast();
        int bottom = supports.stream().mapToInt(BlockPos::getY).min().orElse(worldPosition.getY());
        siteBounds = new AABB(
                Math.min(a.getX(), b.getX()),
                bottom,
                Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1,
                worldPosition.getY() + Shelter.HEIGHT,
                Math.max(a.getZ(), b.getZ()) + 1);
        return siteBounds;
    }

    private boolean occupied() {
        return !level.getEntitiesOfClass(LivingEntity.class, bounds()).isEmpty();
    }

    private static void message(Player p, String key) {
        p.displayClientMessage(Component.translatable("camp.mobilecamp." + key), true);
    }

    private void sound(SoundEvent event, float pitch) {
        level.playSound(null, worldPosition, event, SoundSource.BLOCKS, .65f, pitch);
    }

    /**
     * effects: advances at most one tick, pausing without loading chunks. Active camps do no tick
     * work.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, CampBlockEntity camp) {
        if (!camp.initialized || camp.phase == Phase.ACTIVE) return;
        if (level.isClientSide) {
            if (!camp.paused)
                camp.progress =
                        Math.clamp(
                                camp.progress + (camp.phase == Phase.FOLDING ? -1 : 1),
                                0,
                                Shelter.DURATION);
            return;
        }
        boolean pause = !camp.loaded()
                || (camp.mechanical ? camp.occupied()
                    : camp.phase == Phase.DEPLOYING && camp.progress >= Shelter.DURATION-1 && camp.occupied());
        if (camp.paused != pause) {
            camp.paused = pause;
            camp.sync();
        }
        if (pause) return;
        camp.progress += camp.phase == Phase.FOLDING ? -1 : 1;
        camp.setChanged();
        if (camp.mechanical) {
            int elapsed = camp.phase == Phase.FOLDING ? Shelter.DURATION-camp.progress : camp.progress;
            if (!camp.mechanism().tick(elapsed, camp.phase == Phase.FOLDING)) {
                var player = level.getPlayerByUUID(camp.owner);
                if (player != null) message(player, "mechanism_blocked");
                CampSites.edit(() -> {
                    for (var location : camp.originals.keySet()) if (!location.equals(pos)) {
                        if (level.getBlockEntity(location) instanceof Container container) container.clearContent();
                        level.removeBlockEntity(location);
                    }
                });
                camp.finishPacking();
                return;
            }
        } else if (camp.progress % 20 == 0)
            camp.sound(SoundEvents.PISTON_EXTEND, .6f + camp.progress / 320f);
        if (camp.phase == Phase.DEPLOYING && camp.progress >= Shelter.DURATION) camp.deploy();
        else if (camp.phase == Phase.FOLDING && camp.progress <= 0) camp.finishPacking();
        else if (camp.progress % 10 == 0) camp.sync();
    }

    private void sync() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeVisual(tag);
        tag.put("Carrier", carrier.saveOptional(registries));
        if (owner != null) tag.putUUID("Owner", owner);
        var list = new ListTag();
        originals.forEach(
                (pos, state) -> {
                    var e = new CompoundTag();
                    e.putLong("Position", pos.asLong());
                    e.put("State", NbtUtils.writeBlockState(state));
                    list.add(e);
                });
        tag.put("Originals", list);
    }

    private void writeVisual(CompoundTag tag) {
        tag.putBoolean("Initialized", initialized);
        tag.putBoolean("Mechanical", mechanical);
        tag.putString("Phase", phase.name());
        tag.putInt("Progress", progress);
        tag.putBoolean("Paused", paused);
        tag.putLongArray("Supports", supports.stream().mapToLong(BlockPos::asLong).toArray());
        var items = new ListTag();
        for (var stack : modules)
            items.add(
                    StringTag.valueOf(
                            stack.isEmpty()
                                    ? ""
                                    : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                            .getKey(stack.getItem())
                                            .toString()));
        tag.put("VisualModules", items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        initialized = tag.getBoolean("Initialized");
        mechanical = tag.getBoolean("Mechanical");
        phase = tag.contains("Phase") ? Phase.valueOf(tag.getString("Phase")) : Phase.DEPLOYING;
        progress = Math.clamp(tag.getInt("Progress"), 0, Shelter.DURATION);
        paused = tag.getBoolean("Paused");
        supports = Arrays.stream(tag.getLongArray("Supports")).mapToObj(BlockPos::of).toList();
        if (tag.contains("Carrier")) {
            carrier = ItemStack.parseOptional(registries, tag.getCompound("Carrier"));
            modules = CampCargo.read(carrier, registries);
            owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            originals.clear();
            var list = tag.getList("Originals", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var e = list.getCompound(i);
                originals.put(
                        BlockPos.of(e.getLong("Position")),
                        NbtUtils.readBlockState(
                                registries.lookupOrThrow(Registries.BLOCK),
                                e.getCompound("State")));
            }
        } else {
            var visual = tag.getList("VisualModules", Tag.TAG_STRING);
            var stacks = new ArrayList<ItemStack>();
            for (int i = 0; i < 4; i++) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(visual.getString(i));
                stacks.add(
                        id == null
                                ? ItemStack.EMPTY
                                : new ItemStack(
                                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                                id)));
            }
            modules = stacks;
        }
        plan = null;
        mechanism = null;
        siteBounds = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeVisual(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
