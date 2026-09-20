# First-playable verification — 2026-09-20

Local candidate: `mobilecamp-0.1.0.jar`. No publication or shared-server deployment.

SHA-256: `210ce3142c4f251c10b24f90e4e5252fdd2d08432013d1895fcdc7609090ba22`.

## Checks actually run

- **4 JUnit tests passed:** unique shell and completely clear 2x2x3 bays; all quarter
  rotations; module bounds and duplicate rejection; monotonic mechanism timing.
- **10 real-server GameTests passed:** actual survival item placement, deployed
  equipment, collapse via core interaction, exactly one packed drop, both chest halves,
  rotated redeployment with retained cargo, blocked/wet/unsupported sites without item
  consumption, shared cross-dimension ownership and ledger serialization, cancelled
  placement rollback, attached-block destruction/replacement refusal, block-entity
  reload during deployment and folding, owner/occupant collapse restrictions, field
  kitchen, support/flower restoration, and module menu validation/carrier locking.
- **Real client booth passed**, with Iris/Sodium and Complementary Unbound r5.8.1 in
  the existing Xephyr window, master volume zero. Four checks confirmed synchronized
  full deployment, a genuine client collapse-button packet, exactly one packed pickup,
  and the real synchronized module menu opened through sneak-use. The client exited
  automatically; its test display was closed afterward.
- Production jar inspected: domain classes and mod metadata present; GameTests and
  booth classes absent. Java formatted with google-java-format 1.25.2, AOSP style.

Commands used (JDK 21):

```sh
./gradlew --offline --no-watch-fs test runGameTestServer
./gradlew --offline --no-watch-fs runPhotoBooth jar
```

## Reproduced and corrected during development

1. A cancelled NeoForge placement kept the core and ownership reserved. The failing
   real-item test reproduced this before adding snapshot-rollback support and releasing
   both reservations. The same test then passed.
2. The initial JDK test checked only two bay levels. Testing the full advertised third
   level found lantern collisions. Moving the lanterns outside all bays fixed that test.
3. Flower placement was rejected even though the camp promised restoration of small
   vegetation. A real placement test identified the dandelion's exact obstruction
   coordinate. The explicit flower allowance now passes the complete restoration test.
4. Vanilla bed/chest item model inheritance produced invisible module icons in the
   real menu. Dedicated cuboid icons now render; verified in the client capture.
5. Tall roof geometry used implicit texture coordinates outside the texture tile,
   visibly bleeding magenta onto brace ends. Explicit bounded UVs removed it. Final
   close views and 2x nearest-neighbor crops of the frame/roof joints were inspected.

The uneven-ground fixture also needed an explicit stone footing in its void world;
the initial unsupported site correctly refused placement. Production footing rules
were not weakened to satisfy that fixture.

## Visual evidence

These are real client captures, not concept renders.

- [Completed camp](camp-complete.png)
- [Folding roof wings during deployment](roof-unfolding.png)
- [Workshop and reinforced joints](workshop.png)
- [Exterior collapse switch](collapse-switch.png)
- [Four visible module bays](module-bays.png)

The first pass looked like a plain plank shed; the revision adds a pitched canvas
roof, folding roof wings, reinforced panels and deck, telescoping posts and connected
roof framing. Final inspection found no remaining missing icons or texture bleed.
Visual direction remains subject to Rusty's playtest feedback; automated assertions
do not evaluate taste or claim final artistic approval.

## Practical limits

The booth is isolated singleplayer and server tests use disposable worlds. They do
not constitute a live multiplayer stress test, a process-crash durability test, or
cross-mod compatibility certification. Transformation persistence was tested through
real block-entity serialization/reconstruction; full natural chunk-unload/restart
recovery has not been independently exercised end to end. Furnace block-entity state
uses the vanilla save/load path; a long-running cooking-progress/XP roundtrip is not
separately covered by the current tests.

Recipes are provisional and the module-first crafting proposal is still open.
