# Current rendered item ID fixture

This pack assigns `minecraft:diamond` and `minecraft:stone` separate IDs in
`item.properties`. The graphics smoke test renders both as dropped item
entities and checks that `gbuffers_entities.fsh` sees the matching
`currentRenderedItemId` for each item. A diamond produces a magenta signal, a
stone item produces a green signal, and unmapped items produce black.
