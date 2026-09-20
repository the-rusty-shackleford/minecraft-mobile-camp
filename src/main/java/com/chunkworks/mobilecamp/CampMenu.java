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
    private CampBlockEntity camp;

    public CampMenu(int id, Inventory inventory) {
        this(id, inventory, ItemStack.EMPTY);
    }

    public CampMenu(int id, Inventory inventory, ItemStack carrier) {
        this(id, inventory, carrier, contents(inventory, carrier));
    }

    public CampMenu(int id, Inventory inventory, CampBlockEntity camp) {
        this(id, inventory, ItemStack.EMPTY, contents(inventory, ItemStack.EMPTY));
        this.camp = camp;
        refreshModules();
    }

    private void refreshModules() {
        var modules = camp.editingModules();
        for (int i=0; i<4; i++) getContainer().setItem(i, modules.get(i));
    }

    private CampMenu(int id, Inventory inventory, ItemStack carrier, SimpleContainer container) {
        super(MobileCamp.MENU.get(), id, inventory, container, 1);
        this.carrier = carrier;
        for (int i=0; i<9; i++) {
            var old = slots.get(i);
            final int bay = i;
            var restricted = new Slot(container, i, old.x, old.y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(bay, stack);
                }
                @Override public boolean mayPickup(Player player) { return bay < 4; }
            };
            restricted.index = i;
            slots.set(i, restricted);
        }
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
        if (camp != null) return camp.canEditModules(p);
        return carrier.isEmpty() || p.getMainHandItem() == carrier || p.getOffhandItem() == carrier;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        return slots.get(index).mayPickup(p) ? super.quickMoveStack(p, index) : ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!carrier.isEmpty() && type == ClickType.SWAP && player.getInventory().getItem(button) == carrier) return;
        if (camp == null) { super.clicked(slot, button, type, player); return; }
        if (!stillValid(player)) return;
        refreshModules();
        super.clicked(slot, button, type, player);
        camp.applyModules(IntStream.range(0,4).mapToObj(getContainer()::getItem).toList());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (camp != null) camp.closeModules(this);
    }
}
