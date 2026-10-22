# Entity custom texture fixture

This pack binds `textures/marker.png` through the `texture.gbuffers.marker`
mapping and samples it from `gbuffers_entities.fsh`. The graphics smoke world
renders a cow with the pack enabled and disabled, checking that the sampled
green marker reaches the entity draw.
