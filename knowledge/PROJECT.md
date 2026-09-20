# C.A.M.P.

First playable release, Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21.
Repository: `minecraft-mobile-camp`; mod id and artifact name: `mobilecamp`.
Version 0.1.0 is the first playable release. On 2026-09-20 Rusty explicitly requested
release, then directed a warning before disconnecting online players for the restart.
They extended the original 120-second countdown to five minutes. The countdown was
subsequently paused for inventory recovery, then restarted with a fresh five-minute
server announcement. This authorizes publication, the pack update and the warned
restart, superseding the earlier release hold and empty-server-only restart condition
for this deployment. The live pack was verified as 1.50.1; C.A.M.P. is published in 1.51.0.
Unrelated unreleased changes remain held.

Deployment completed on 2026-09-20 after the renewed five-minute warning. The server
loaded C.A.M.P. 0.1.0, matched published pack 1.51.0, and reported 20 TPS with players
reconnected. See [release verification](../devtools/verification/release-0.1.0.md).

## Product and decisions

The accepted brief is [D-0001](decisions/D-0001.md): a rugged, mechanically unfolding
designed shelter with attached modular equipment, mixing a closed rear room and
covered outdoor workshop. [D-0002](decisions/D-0002.md) adds the single deployed camp
limit and exterior collapse control, and identifies delegated implementation defaults.

## Implementation

- 8x8 platform, six-block site clearance for a pitched canvas roof. Reinforced wood
  panels, telescoping metal posts, hinged deck and roof wings, and staged mechanical
  sounds. Transformation takes eight seconds in each direction.
- Sleeping, double-chest storage, crafting and cooking bays; a field kitchen adds a
  smoker. Equipment becomes actual vanilla blocks when deployment finishes.
- Nested module cargo preserves both chest halves and block-entity state. Module
  swaps occur in the packed item's screen and persist immediately.
- An overworld ownership ledger enforces one deployed camp per player across all
  dimensions. Per-dimension reservations protect the complete camp site, even if its
  anchor is unloaded. Cancelled NeoForge placement rolls back both ledgers.
- Core right-click or mining requests collapse. Owner/operator only, with a clear
  interior. The packed camp is dropped only after terrain restoration.
- Solid obstructions, fluids, block entities, overlaps and unloaded sites refuse
  placement without spending the item. Small plants and flowers are restored on
  packing. Four corner supports bridge gaps up to three blocks.
- Block-entity persistence stores transformation progress and site state. Operations
  pause if the reserved site is unloaded. No forced chunk loading or idle camp work.

## Current design discussion

Crafting balance remains open. The prototype directly crafts a complete camp with
two iron blocks, piston, two chests, green bed, crafting table, furnace and leather.
Its separate module recipes currently cost their representative station plus four
iron ingots and leather. These are implementation placeholders.

The proposed replacement crafts a chassis from four prebuilt starter modules,
two iron blocks, a piston and leather. Each module uses the actual equipment plus
two iron ingots and leather; the sleeping recipe accepts any bed, cooking requires
both furnace and campfire, and field kitchen requires furnace, smoker and campfire.
Do not treat this discussion as a finalized balance decision.

## Verification and remaining review

See `devtools/verification/first-playable.md` for tests run and visual evidence.
The first visual pass found missing bed/chest module icons and an overly plain shell;
those were revised before the final pass. Playtest feedback still governs the artistic
direction and crafting economy. Do not describe source compilation alone as a playtest.

The module API requires compatible part coordinates across definition changes;
there is no migration system for incompatible add-on module revisions yet. Natural
long-running chunk-unload/restart recovery and cross-mod compatibility deserve wider
playtesting beyond the isolated persistence and cancellation tests.
