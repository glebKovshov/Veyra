# Custom uniforms

Place this folder in `shaderpacks/` and select it with F6 in a world at noon. `shaders.properties` defines OptiFine/Iris `uniform.*` and `variable.*` expressions (float, int, bool, helper variable, line continuation). Magenta means terrain and `final` received the expected CPU-evaluated values, including the pack-defined `shadowFade` expression and integer override of Veyra's `framemod8`; green means a value was wrong.

This is an original diagnostic fixture used by the software Vulkan integration matrix.
The generated `custom-uniforms` world includes a biome with cloud height 224,
distinct from the dimension default. Terrain and final both require that camera
attribute; the default biome in an ordinary world intentionally produces green.

The helper `veyraHalf` and integer `veyraDayTick` are deliberately declared after consumers. Both terrain and final must receive the current-frame values after dependency ordering; declaration order in shader uniform blocks remains stable.

The final pass also checks live celestial and integer camera vectors followed by scalar fields. This catches post-uniform std140 packing regressions where a scalar must occupy the fourth word after a vec3 or ivec3.

The terrain signal also requires `gbufferProjection * gbufferProjectionInverse` to preserve a probe vector, catching mixed native/screen-space projection suppliers.

The legacy `gdepthFormat` directive requests `RGB10_A2` storage for colortex1; the GPU test verifies the selected physical format before testing rollback and pack switching.
