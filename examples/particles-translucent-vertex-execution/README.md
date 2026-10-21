# Translucent particle vertex execution

This Iris-style fixture runs a custom `gbuffers_particles_translucent.vsh` on
Minecraft's particle vertex format. The fragment shader discards particles
unless it receives the custom vertex signal, then writes magenta so the real
client smoke test can compare the enabled and disabled frames.
