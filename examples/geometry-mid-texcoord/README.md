# Terrain per-quad UV midpoint

This fixture checks Iris' per-quad UV midpoint inputs on terrain. Veyra stores the average of each four-vertex terrain quad in VulkanMod's extended compressed terrain vertex format, then derives `signMidCoordPos` and the flat `absMidCoordPos` offset from that midpoint and the original UV at each vertex.

The Lavapipe smoke test expects both magenta and green terrain regions while the shader is enabled. If the midpoint attribute is missing or either derived input is zero, the magenta signal disappears. With shaders disabled, the magenta shader signal must disappear. This synthetic fixture validates Veyra's terrain data path; it is not a third-party pack visual result.
