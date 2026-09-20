/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialization boundary. AF: four bay items and their nested cargo. RI: exactly four stacks, each
 * empty or compatible with its bay. Returned stacks and tags are defensive copies.
 */
public final class CampCargo {
    private CampCargo() {}

    /** effects: returns private custom data copy, empty if absent; throws: none. */
    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** effects: replaces custom data with a defensive copy; throws: none. */
    public static void data(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** requires: registry lookup; effects: reads four modules, initializes only brand-new camps. */
    public static List<ItemStack> read(ItemStack camp, HolderLookup.Provider registries) {
        var tag = data(camp);
        var result = new ArrayList<ItemStack>();
        if (!tag.getBoolean("Initialized"))
            return List.of(
                    new ItemStack(MobileCamp.SLEEP.get()),
                    new ItemStack(MobileCamp.STORAGE.get()),
                    new ItemStack(MobileCamp.WORK.get()),
                    new ItemStack(MobileCamp.COOK.get()));
        var modules = tag.getList("Modules", Tag.TAG_COMPOUND);
        for (int i = 0; i < 4; i++) {
            var stack =
                    i < modules.size()
                            ? ItemStack.parseOptional(registries, modules.getCompound(i))
                            : ItemStack.EMPTY;
            if (!stack.isEmpty() && (!(stack.getItem() instanceof ModuleItem m) || m.bay() != i))
                throw new IllegalArgumentException("Invalid camp module in bay " + i);
            result.add(stack);
        }
        return result;
    }

    /** requires: four valid bay items; effects: stores independent module copies. */
    public static void write(
            ItemStack camp, List<ItemStack> modules, HolderLookup.Provider registries) {
        if (modules.size() != 4) throw new IllegalArgumentException("four bays required");
        var tag = data(camp);
        var list = new ListTag();
        for (var module : modules) list.add(module.saveOptional(registries));
        tag.putBoolean("Initialized", true);
        tag.put("Modules", list);
        data(camp, tag);
    }
}
