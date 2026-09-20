/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.mobilecamp.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Immutable expedition frame. AF: cells describe the attached shell in crate-relative coordinates.
 * RI: cells are unique, exclude the core, and lie within the 8x8x5 enclosure.
 */
public final class Shelter {
    private Shelter() {}

    public static final int DURATION = 160;
    public static final int HEIGHT = 6;

    public enum Kind {
        DECK,
        POST,
        WALL,
        WINDOW,
        CANVAS,
        LANTERN
    }

    /** AF: integer lattice offset. RI: all integer coordinates are legal. */
    public record Pos(int x, int y, int z) {
        /** requires: any turns; effects: returns rotation about Y; throws: none. */
        public Pos rotate(int turns) {
            return switch (Math.floorMod(turns, 4)) {
                case 1 -> new Pos(-z, y, x);
                case 2 -> new Pos(-x, y, -z);
                case 3 -> new Pos(z, y, -x);
                default -> this;
            };
        }
    }

    /** AF: one structural part and its deployment phase. RI: nonnull position/kind. */
    public record Part(Pos pos, Kind kind) {
        public Part {
            java.util.Objects.requireNonNull(pos);
            java.util.Objects.requireNonNull(kind);
        }
    }

    /** requires: none; effects: returns the immutable standard shell; throws: none. */
    public static List<Part> frame() {
        var parts = new ArrayList<Part>();
        for (int x = -3; x <= 4; x++)
            for (int z = 0; z <= 7; z++) {
                if (x != 0 || z != 0) parts.add(new Part(new Pos(x, 0, z), Kind.DECK));
                parts.add(new Part(new Pos(x, 4, z), Kind.CANVAS));
                boolean side = x == -3 || x == 4;
                boolean rear = z == 0;
                boolean corner = side && (z == 0 || z == 3 || z == 7);
                if (corner) {
                    for (int y = 1; y <= 3; y++) parts.add(new Part(new Pos(x, y, z), Kind.POST));
                } else if (rear || (side && z < 4) || (z == 3 && x != 0 && x != 1)) {
                    for (int y = 1; y <= 3; y++)
                        parts.add(
                                new Part(
                                        new Pos(x, y, z),
                                        y == 2 && (side || rear) ? Kind.WINDOW : Kind.WALL));
                }
            }
        parts.add(new Part(new Pos(-1, 3, 4), Kind.LANTERN));
        parts.add(new Part(new Pos(2, 3, 4), Kind.LANTERN));
        return List.copyOf(parts);
    }

    /**
     * requires: none; effects: returns four immutable bay origins in sleep/storage/work/cook order.
     */
    public static List<Pos> bays() {
        return List.of(new Pos(-2, 1, 1), new Pos(2, 1, 1), new Pos(-2, 1, 5), new Pos(2, 1, 5));
    }

    /**
     * requires: positions for one equipment bay; effects: checks unique bounded 2x2x3 parts;
     * throws: IllegalArgumentException for out-of-bounds or duplicate parts.
     */
    public static void validateModule(List<Pos> positions) {
        var seen = new HashSet<Pos>();
        for (var p : positions)
            if (p.x < 0 || p.x > 1 || p.y < 0 || p.y > 2 || p.z < 0 || p.z > 1 || !seen.add(p))
                throw new IllegalArgumentException("Module parts must be unique within 2x2x3");
    }

    /** requires: finite ticks; effects: returns smooth progress of a part's mechanism in [0,1]. */
    public static double progress(Kind kind, double ticks) {
        int start =
                switch (kind) {
                    case DECK -> 12;
                    case POST -> 45;
                    case WALL, WINDOW -> 65;
                    case LANTERN -> 120;
                    case CANVAS -> 105;
                };
        double t = Math.clamp((ticks - start) / 40.0, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
