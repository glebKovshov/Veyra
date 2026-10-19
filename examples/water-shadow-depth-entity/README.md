# Separate shadow depth readback

Original test shaders author depth 0.75 for opaque terrain/entity features and 0.25 for translucent water. The main water/entity shader must read 0.25 from shadowtex0 and 0.75 from shadowtex1 at the same coordinate. An alias of the two images, a snapshot after translucency or a cleared/default map cannot pass. The world includes a water layer; entity placement follows the actual camera. This tests depth-copy timing/binding rather than third-party lighting or arbitrary shaderpack vertex code.
