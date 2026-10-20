# Current rendered item ID fixture

This pack assigns `minecraft:diamond` and `minecraft:stone` separate IDs in
`item.properties`. The graphics smoke test equips the player with a diamond in
the main hand and stone in the offhand, then checks that `gbuffers_hand.fsh`
sees each matching `currentRenderedItemId`. A diamond produces a magenta
signal, a stone item produces a green signal, and unmapped items produce black.
