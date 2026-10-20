# Terrain per-quad UV midpoint

This fixture checks the terrain bridge's Iris per-quad UV midpoint. Veyra stores the average of each four-vertex terrain quad in VulkanMod's extended compressed terrain vertex format, then derives `signMidCoordPos` from that midpoint and the original UV at each vertex.

The Lavapipe smoke test expects both magenta and green terrain regions while the shader is enabled. If the midpoint attribute is missing or left at zero, one of the two signals disappears. With shaders disabled, the magenta shader signal must disappear. This synthetic fixture validates Veyra's terrain data path; it is not a third-party pack visual result.
