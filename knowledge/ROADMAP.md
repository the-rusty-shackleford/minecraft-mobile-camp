# First playable C.A.M.P.

The accepted product brief is D-0001. Rusty subsequently asked to continue development
without routine approval interruptions; the implementation defaults below are chosen
under that delegation and remain subject to the playtest.

1. An 8 by 8 platform, with six blocks of clearance for a pitched roof, a closed rear room and
   covered front workshop. Four bays: sleeping, storage, crafting and cooking.
2. A placed crate unfolds automatically; the exterior red switch or mining the core
   packs the camp into one item. One deployed camp per player across all dimensions.
   Everyone steps outside before owner/operator retraction. Equipment remains attached and usable.
3. All four bays are equipped in the starter item. Sneak-use the packed item to exchange
   modules; the first upgrade adds a smoker to the cooking bay. Module cargo travels
   with that module and keeps both halves of a double chest and furnace state.
4. Clear terrain only: refuse solid obstructions, liquids and existing block entities.
   Corner supports bridge small gaps, up to three blocks. Preserve displaced vegetation
   and restore the site when packing; never capture player-built blocks as cargo.
5. Save transformation progress and ownership. Pause at unloaded chunk boundaries;
   resume after reload. Protect attached blocks from separate harvesting or piston moves.
6. Choreograph supports, extending/hinged floor sections, walls, equipment and canvas
   roof. Reverse the sequence to pack. Coordinate existing mechanical game sounds.
7. Verify JDK-only layout/state rules, actual server placement/interaction/packing,
   inventories, interruptions and module exchange, then review the rendered transformation
   in the single permitted client with a muted booth.

No release, pack update or server restart is authorised. The bed uses normal position-based
respawn behavior: sleep after moving camp to set the new location. Inventory and cooking
state are preserved in transit; cooking does not continue while packed.
