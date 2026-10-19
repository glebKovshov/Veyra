# Terrain and entity view-space inputs

This example uses the Iris `viewPos` varying in both terrain and entity fragment programs. Veyra derives terrain view position from VulkanMod's camera-relative terrain position and the current `gbufferModelView` matrix, then passes it to the fragment shader.

The example demonstrates shader compilation and input adaptation. It is not a third-party shaderpack visual-compatibility result.
