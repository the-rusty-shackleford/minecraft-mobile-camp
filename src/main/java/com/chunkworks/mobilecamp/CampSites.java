/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent reservation index, including unloaded camps. AF: position -> its camp anchor. RI:
 * reservations do not overlap. Mutations occur only on the server thread; no chunk loading. Scoped
 * internal edits bypass protection only while constructing/restoring a camp.
 */
public final class CampSites extends SavedData {
    private final Map<Long, Long> owners = new HashMap<>();
    private static final ThreadLocal<Integer> EDIT_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** effects: returns level's persistent index; throws: persistence errors. */
    public static CampSites get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new Factory<>(CampSites::new, CampSites::load), "mobilecamp_sites");
    }

    private static CampSites load(CompoundTag tag, HolderLookup.Provider registries) {
        var sites = new CampSites();
        var entries = tag.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.getCompound(i);
            long core = e.getLong("Core");
            for (long p : e.getLongArray("Positions")) sites.owners.put(p, core);
        }
        return sites;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var grouped = new HashMap<Long, List<Long>>();
        owners.forEach((p, o) -> grouped.computeIfAbsent(o, k -> new ArrayList<>()).add(p));
        var entries = new ListTag();
        grouped.forEach(
                (o, ps) -> {
                    var e = new CompoundTag();
                    e.putLong("Core", o);
                    e.putLongArray("Positions", ps);
                    entries.add(e);
                });
        tag.put("Sites", entries);
        return tag;
    }

    /** effects: returns owner or null; throws: none. */
    public BlockPos owner(BlockPos pos) {
        var owner = owners.get(pos.asLong());
        return owner == null ? null : BlockPos.of(owner);
    }

    /** requires: non-overlapping positions; effects: reserves every position; throws: overlap. */
    public void claim(BlockPos core, List<BlockPos> positions) {
        for (var p : positions)
            if (owners.containsKey(p.asLong()) && owners.get(p.asLong()) != core.asLong())
                throw new IllegalStateException("Overlapping camp");
        for (var p : positions) owners.put(p.asLong(), core.asLong());
        setDirty();
    }

    /** effects: releases only this camp's reservations. */
    public void release(BlockPos core) {
        owners.values().removeIf(o -> o == core.asLong());
        setDirty();
    }

    /** effects: reports whether an internal edit is in progress on this thread. */
    public static boolean editing() {
        return EDIT_DEPTH.get() > 0;
    }

    /**
     * requires: synchronous server-thread operation; effects: allows its camp edits, even on throw.
     */
    public static void edit(Runnable operation) {
        EDIT_DEPTH.set(EDIT_DEPTH.get() + 1);
        try {
            operation.run();
        } finally {
            EDIT_DEPTH.set(EDIT_DEPTH.get() - 1);
        }
    }
}
