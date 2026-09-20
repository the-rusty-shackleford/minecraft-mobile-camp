/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

import java.util.stream.IntStream;

/**
 * Four equipment bays in a one-row vanilla container. AF: first four slots are camp modules. RI:
 * remaining slots unusable; carrier cannot move while editing; changes saved immediately.
 */
public final class CampMenu extends ChestMenu {
    private final ItemStack carrier;

    public CampMenu(int id, Inventory inventory) {
        this(id, inventory, ItemStack.EMPTY);
    }

    public CampMenu(int id, Inventory inventory, ItemStack carrier) {
        this(id, inventory, carrier, contents(inventory, carrier));
    }

    private CampMenu(int id, Inventory inventory, ItemStack carrier, SimpleContainer container) {
        super(MobileCamp.MENU.get(), id, inventory, container, 1);
        this.carrier = carrier;
        for (int i = 9; i < slots.size(); i++) {
            var old = slots.get(i);
            if (!carrier.isEmpty() && old.getItem() == carrier) {
                var locked =
                        new Slot(inventory, old.getContainerSlot(), old.x, old.y) {
                            @Override
                            public boolean mayPickup(Player p) {
                                return false;
                            }

                            @Override
                            public boolean mayPlace(ItemStack s) {
                                return false;
                            }
                        };
                locked.index = i;
                slots.set(i, locked);
            }
        }
    }

    private static SimpleContainer contents(Inventory inventory, ItemStack carrier) {
        var c =
                new SimpleContainer(9) {
                    @Override
                    public boolean canPlaceItem(int slot, ItemStack stack) {
                        return slot < 4
                                && stack.getItem() instanceof ModuleItem m
                                && m.bay() == slot;
                    }
                };
        if (!carrier.isEmpty()) {
            var modules = CampCargo.read(carrier, inventory.player.registryAccess());
            for (int i = 0; i < 4; i++) c.setItem(i, modules.get(i));
            CampCargo.write(carrier, modules, inventory.player.registryAccess());
            c.addListener(
                    changed ->
                            CampCargo.write(
                                    carrier,
                                    IntStream.range(0, 4).mapToObj(c::getItem).toList(),
                                    inventory.player.registryAccess()));
        }
        return c;
    }

    @Override
    public boolean stillValid(Player p) {
        return carrier.isEmpty() || p.getMainHandItem() == carrier || p.getOffhandItem() == carrier;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return slots.get(index).mayPickup(p) ? super.quickMoveStack(p, index) : ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (type == ClickType.SWAP && player.getInventory().getItem(button) == carrier) return;
        super.clicked(slot, button, type, player);
    }
}
