/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import com.chunkworks.mobilecamp.domain.Shelter;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Extension point for attached equipment. AF: an item installs immutable block parts into one bay.
 * RI: bay is 0..3, parts unique in 2x2x3; definitions are immutable. Cargo lives on the item stack.
 * Add-ons register their own ModuleItem to extend a bay without changing the packing protocol.
 */
public class ModuleItem extends Item {
    public record Part(Shelter.Pos pos, BlockState state) {
        public Part {
            java.util.Objects.requireNonNull(pos);
            java.util.Objects.requireNonNull(state);
        }
    }

    private final int bay;
    private final List<Part> parts;

    /**
     * requires: registered states, bay 0..3; effects: creates definition; throws: invalid bounds.
     */
    public ModuleItem(int bay, List<Part> parts) {
        super(new Item.Properties().stacksTo(1));
        if (bay < 0 || bay > 3) throw new IllegalArgumentException("bay");
        Shelter.validateModule(parts.stream().map(Part::pos).toList());
        this.bay = bay;
        this.parts = List.copyOf(parts);
    }

    /** effects: returns compatible bay; throws: none. */
    public final int bay() {
        return bay;
    }

    /** effects: returns immutable geometry; throws: none. */
    public final List<Part> parts() {
        return parts;
    }
}
