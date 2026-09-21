# C.A.M.P. 0.3.0

Adds the Cartography Module in the existing workshop bay, retaining the crafting table alongside a vanilla cartography table. Compatible with Magical Map atlas creation, binding and extraction. Craft the module from three iron ingots, a cartography table and leather, then install it through the blue module control.

Minecraft 1.21.1 · NeoForge 21.1.248 · Java 21. Install on both client and server.

Clean release validation: 4 JUnit tests, 18 real-server GameTests and the complete muted NVIDIA RTX 4070 / Iris / Complementary client walkthrough passed.

Rusty explicitly authorized publication and deployment of all three mods on
September 20, 2026, requesting a two-minute server warning. This supersedes the
earlier release holds. The clean jar is byte-identical to the reviewed candidate.

Artifact: `mobilecamp-0.3.0.jar`

SHA-1: `0d8e62448c94bcf1294bb6668a98669a495b27bc`

SHA-256: `bf830839a398a411223904d2d96562b43f666a1e9242700b3764e89f761df210`

## Published and deployed

[Version 0.3.0](https://github.com/the-rusty-shackleford/minecraft-mobile-camp/releases/tag/v0.3.0)
is published and deployed on both sides of pack **1.54.0**. The downloaded GitHub
asset and installed server jar match the clean build hashes above. The pack
changes only C.A.M.P. 0.3.0, Vanilla Wheels 1.9.0, Craftlight 0.1.0 and the
version label from the verified 1.53.0 baseline. Unrelated entries and overrides
are identical, and both HTTP pack downloads match the staged archives.

The warning was delivered at 2026-09-21 01:20:27 UTC. Rusty then explicitly
authorized disconnecting remaining players after the interval. The two remaining
players were disconnected, the world save flushed, and zero-player checks passed
before restart at 01:25:31 UTC. Fresh startup reached Done at 01:25:45 UTC.

All three released versions loaded. Mod Hub reports pack/server parity and RCON
reports 20 TPS overall and in each dimension. Startup has the same 36 error
messages as the prior baseline, with no added errors. World settings, operators,
whitelist, Distant Horizons and paused Chunky configuration were preserved.
Rusty's personal client and the retained Magical Map test instance were unchanged.

This verifies deployment and server startup alongside the isolated gameplay and
GPU checks above. Independent multiplayer and full-pack client playtesting of
the new features remain unverified.
