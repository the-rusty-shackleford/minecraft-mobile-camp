/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Cross-dimension active camp ledger, saved in the overworld. AF: player UUID -> active camp. RI:
 * at most one reservation per player, including camps in unloaded chunks.
 */
public final class CampOwners extends SavedData {
    public record Site(String dimension, long position) {}

    private final Map<UUID, Site> owners = new HashMap<>();

    public static CampOwners get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new Factory<>(CampOwners::new, CampOwners::load), "mobilecamp_owners");
    }

    private static CampOwners load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new CampOwners();
        var list = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            result.owners.put(
                    e.getUUID("Player"), new Site(e.getString("Dimension"), e.getLong("Position")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        owners.forEach(
                (id, site) -> {
                    var e = new CompoundTag();
                    e.putUUID("Player", id);
                    e.putString("Dimension", site.dimension());
                    e.putLong("Position", site.position());
                    list.add(e);
                });
        tag.put("Owners", list);
        return tag;
    }

    /** effects: returns player's active site or null. */
    public Site active(UUID id) {
        return owners.get(id);
    }

    /** requires: player has no active site; effects: reserves site; throws: duplicate ownership. */
    public void claim(UUID id, ServerLevel level, BlockPos pos) {
        if (owners.putIfAbsent(id, new Site(level.dimension().location().toString(), pos.asLong()))
                != null) throw new IllegalStateException("Player already has a camp");
        setDirty();
    }

    /** effects: releases only a matching site; never deletes another camp's ownership. */
    public void release(UUID id, ServerLevel level, BlockPos pos) {
        if (owners.remove(id, new Site(level.dimension().location().toString(), pos.asLong())))
            setDirty();
    }
}
