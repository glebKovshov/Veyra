# Terrain and entity view-space inputs

This example uses the Iris `viewPos` varying in both terrain and entity fragment programs. Veyra derives terrain view position from VulkanMod's camera-relative terrain position and the current `gbufferModelView` matrix, then passes it to the fragment shader. The terrain signal is magenta when all coordinates are finite-range and nonzero; green identifies an invalid or missing position.

The Lavapipe smoke fixture reads back the terrain signal while shaders are enabled and disabled. This checks the synthetic Veyra bridge; it is not a third-party shaderpack visual-compatibility result.
