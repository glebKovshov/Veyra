# Textured pipeline default vertex attributes

This Iris-style fixture targets Minecraft's world-border `POSITION_TEX` pipeline.
It declares `mc_Entity` and `mc_midTexCoord` and draws its magenta signal only
when both resolve to OpenGL's generic vertex-attribute default `(0, 0, 0, 1)`.
The fixture checks the translated shader in the real client and verifies that
disabling Veyra restores the vanilla pipeline.

The pack also provides a solid `gbuffers_terrain` program. Otherwise Iris'
terrain fallback chain would route terrain through `gbuffers_textured`, whose
discard would remove the opaque background behind the additive border signal.
