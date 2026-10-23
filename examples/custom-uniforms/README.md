# Custom uniforms

Place this folder in `shaderpacks/` and select it with F6 in a world at noon. `shaders.properties` defines OptiFine/Iris `uniform.*` and `variable.*` expressions (float, int, bool, helper variable, line continuation). Magenta means terrain and `final` received the expected CPU-evaluated values, including the pack-defined `shadowFade` expression and integer override of Veyra's `framemod8`; green means a value was wrong.

This is an original diagnostic fixture used by the software Vulkan integration matrix.

The final pass also checks live celestial and integer camera vectors followed by scalar fields. This catches post-uniform std140 packing regressions where a scalar must occupy the fourth word after a vec3 or ivec3.
