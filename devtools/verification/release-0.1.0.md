# Release verification — 0.1.0

Released on 2026-09-20 with explicit authorization. Minecraft 1.21.1, NeoForge
21.1.248, Java 21. [GitHub release](https://github.com/the-rusty-shackleford/minecraft-mobile-camp/releases/tag/v0.1.0).

## Artifact and release gates

- Ran `./gradlew --offline --no-watch-fs clean build` with JDK 21 and the real client
  booth under Iris/Sodium and Complementary Unbound. Build succeeded: four domain
  tests, ten required real-server GameTests, and the client deployment, collapse,
  pickup and module-menu checks passed. No gates were skipped.
- Published `mobilecamp-0.1.0.jar` from source release commit `c780aff`, tagged `v0.1.0`.
  Downloaded the GitHub asset and compared it byte for byte with the built jar.
- SHA-1: `67fe1f95ee525bcdbac732c511f725c7982061bc`.
- SHA-256: `210ce3142c4f251c10b24f90e4e5252fdd2d08432013d1895fcdc7609090ba22`.
- Artifact size: 85,589 bytes. GitHub release is published, not a draft or prerelease.

## Pack and live server

- Published pack 1.51.0 from the verified live 1.50.1 manifest. C.A.M.P. is the only
  added file; all previous entries remain identical. It is required on both client
  and server. Both assembled pack archives contain the correct version and jar hash.
- The initial 120-second warning was cancelled and extended to five minutes at
  Rusty's request. After an inventory-recovery interruption, the server received a
  fresh five-minute warning and a final one-minute reminder. Both full intervals
  elapsed before the restart request at 19:49:27 UTC.
- Flushed the world save before restart. Verified a new startup log beginning at
  19:49:31 UTC and `Done` at 19:49:41 UTC, rather than relying on the prior process's log.
- The live loaded-mod list contains `C.A.M.P. 0.1.0 (mobilecamp)`. The installed jar's
  SHA-1 matches the published artifact. Mod Hub reports the server matches the
  published pack. Players reconnected and overall performance measured 20 TPS.

The existing pack reports warnings and recipe errors for other mods; this is not a
claim of an error-free pack. No C.A.M.P. startup or recipe error was found. This live
check verifies deployment and server health; the deployment/packing interaction gate
was exercised in the isolated real client, not by placing a camp in a player's world.
The wider compatibility and durability limits in [first-playable verification](first-playable.md)
still apply. Crafting balance remains provisional.
