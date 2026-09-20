/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

/**
 * Partitions: all rotations, shell/bay collision, module bounds/duplicates, phase endpoints/order.
 */
class ShelterTest {
    @Test
    void completeUniqueShellLeavesBaysAndCoreFree() {
        var cells = new HashSet<Shelter.Pos>();
        for (var p : Shelter.frame()) {
            assertTrue(cells.add(p.pos()), "duplicate " + p);
            assertTrue(
                    p.pos().x() >= -3 && p.pos().x() <= 4 && p.pos().z() >= 0 && p.pos().z() <= 7);
            assertTrue(p.pos().y() >= 0 && p.pos().y() <= 4);
        }
        assertFalse(cells.contains(new Shelter.Pos(0, 0, 0)));
        for (var bay : Shelter.bays())
            for (int x = 0; x < 2; x++)
                for (int y = 0; y < 3; y++)
                    for (int z = 0; z < 2; z++)
                        assertFalse(
                                cells.contains(
                                        new Shelter.Pos(bay.x() + x, bay.y() + y, bay.z() + z)));
        assertEquals(
                64, Shelter.frame().stream().filter(p -> p.kind() == Shelter.Kind.CANVAS).count());
    }

    @Test
    void rotationIsBijectionAndPreservesDistances() {
        for (int turn = -4; turn <= 4; turn++) {
            var cells = new HashSet<Shelter.Pos>();
            for (var part : Shelter.frame()) {
                var p = part.pos();
                var r = p.rotate(turn);
                assertTrue(cells.add(r));
                assertEquals(p, r.rotate(-turn));
                assertEquals(p.x() * p.x() + p.z() * p.z(), r.x() * r.x() + r.z() * r.z());
            }
        }
    }

    @Test
    void modulesEnforceBoundsAndUniqueness() {
        var p = new Shelter.Pos(0, 0, 0);
        assertDoesNotThrow(() -> Shelter.validateModule(List.of(p, new Shelter.Pos(1, 2, 1))));
        assertThrows(IllegalArgumentException.class, () -> Shelter.validateModule(List.of(p, p)));
        for (var invalid :
                List.of(
                        new Shelter.Pos(-1, 0, 0),
                        new Shelter.Pos(2, 0, 0),
                        new Shelter.Pos(0, 3, 0),
                        new Shelter.Pos(0, 0, 2)))
            assertThrows(
                    IllegalArgumentException.class, () -> Shelter.validateModule(List.of(invalid)));
    }

    @Test
    void mechanismsAreMonotonicAndFinish() {
        for (var kind : Shelter.Kind.values()) {
            assertEquals(0, Shelter.progress(kind, 0));
            assertEquals(1, Shelter.progress(kind, Shelter.DURATION));
            double last = 0;
            for (int t = 0; t <= Shelter.DURATION; t++) {
                double p = Shelter.progress(kind, t);
                assertTrue(p >= last && p <= 1);
                last = p;
            }
        }
        assertTrue(
                Shelter.progress(Shelter.Kind.DECK, 50)
                        > Shelter.progress(Shelter.Kind.CANVAS, 50));
    }
}
