# Terrain and entity view-space inputs

This example uses the Iris `viewPos` varying in both terrain and entity fragment programs. Veyra derives terrain view position from VulkanMod's camera-relative terrain position and the current `gbufferModelView` matrix, then passes it to the fragment shader. The terrain signal is magenta when all coordinates are finite-range and nonzero; green identifies an invalid or missing position.

The Lavapipe smoke fixture reads back the terrain signal from the presented `colortex0` image while shaders are enabled and disabled. This example declares no auxiliary MRT target, so its check does not require `colortex1`. It checks the synthetic Veyra bridge; it is not a third-party shaderpack visual-compatibility result.
