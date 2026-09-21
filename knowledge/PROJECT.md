# C.A.M.P.

Version **0.3.0** is published and deployed in pack **1.54.0**.
See [release and deployment verification](../devtools/verification/release-0.3.0.md).

Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21.
Repository: minecraft-mobile-camp; mod id and artifact: mobilecamp.

## Current work

Version 0.3.0 adds the acknowledged [Cartography Module](decisions/D-0004.md).
It installs through the existing third bay, retaining the crafting table and
adding vanilla cartography. Four domain tests, eighteen real-server GameTests and
the muted RTX 4070 shader booth passed, including actual Magical Map 0.1.0 menu
operations. See [verification](../devtools/verification/cartography-module.md).
Rusty authorized publication and deployment on September 20, 2026; see the release verification record.


Version 0.2.0 was published and deployed with Schnappviecher 0.1.1 in pack
1.52.0 on 2026-09-20. The authorized restart followed a 120-second warning and
countdown reminders; startup completed at 21:32:01 UTC. Both installed artifact
hashes match the releases, Mod Hub reports the server matches the published pack,
and the server reported 20 TPS. Unrelated held mod changes remain held.
Players should update their pack and pack/redeploy existing camps for the new layout.
See [deployment evidence](../devtools/verification/0.2.0.md#live-deployment).

[D-0001](decisions/D-0001.md) defines the designed expedition shelter and attached
equipment. [D-0002](decisions/D-0002.md) sets the single deployed camp and collapse
control. [D-0003](decisions/D-0003.md) records the requested physical pistons,
full foundations, blue module control, bedroll and placement-loss correction.

## Implementation

- 8x8 camp inside a reserved 10x8x6 mechanism volume. Real redstone-powered vanilla
  pistons move both wings in five layers during the eight-second deployment and
  folding sequence. Fixed parts and equipment are stored/installed by the core.
- Grounded deck with foundation columns under the whole footprint, bridging at
  most three blocks. Static asset audit reduced 341 coplanar overlaps to zero.
- Separate blue module control opens the deployed editor on ordinary right-click.
  A single editor lease prevents conflicting collapse; actual slots enforce bay
  compatibility. Module removal/reinsertion retains live station cargo.
- Bedroll permits sleep without changing respawn. Legacy camp beds are protected;
  ordinary beds still set spawn. Unsafe dimensions refuse sleep without explosion.
- Rejected placement retains the entire packed item. Clients do not pre-consume it;
  the server explicitly resends unchanged inventory on site/ownership rejection.
- Craftable, independently placeable structural blocks and module mounts support
  manual piston builds. The core provides automatic equipment and cargo handling.
- Ownership and site ledgers persist across dimensions and unloaded camps.
  External piston moves remain prohibited. Occupied or unloaded sites pause.
  Cancelled internal strokes restore terrain and return one cargo-bearing pickup.
- Legacy saves finish their old transaction and adopt the new mechanism on
  redeployment. No forced rewrite of an occupied existing camp.

## Verification

Four domain tests and sixteen real-server GameTests passed locally, including
real inventory packets, both hands, module controls, night sleep, every mechanical
stroke in both directions, core reload during motion, occupied actuator strips
and cancelled-piston recovery. The standalone model audit passes over 28 models
and passes mypy --strict. The shader/client booth also passed, including survival rejection, deployed module
exchange and actual collapse packets. Rendered close-ups were inspected.
See devtools/verification/0.2.0.md for the final evidence and limitations.

## Crafting balance

Prototype complete-camp and module recipes remain. New independently craftable
structural parts have provisional recipes described in the README.
The proposed chassis-from-four-modules recipe and reduced module iron cost remain
discussion, not an accepted crafting overhaul.

The Java module API requires stable part coordinates because cargo is keyed by
them. There is no migration framework for incompatible add-on module revisions.
Wider cross-mod and natural chunk-unload/restart playtesting remain useful beyond
the isolated real-server regressions.

## Released history

0.1.0 was released and deployed on 2026-09-20 in pack 1.51.0 after the then-requested
five-minute warning. The server loaded the matching artifact and reported 20 TPS.
See [release verification](../devtools/verification/release-0.1.0.md).

## Release authorization — September 20, 2026

Rusty requested: "release it all, 2 minute server warning". Version 0.3.0 is
authorized for public source/jar publication and pack 1.54.0 deployment. The clean
build passed 4 JUnit tests, 18 real-server GameTests and the complete
GPU shader booth. See [release verification](../devtools/verification/release-0.3.0.md).
Earlier release holds are superseded for this version.
