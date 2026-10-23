# Standalone entity normal readback

This original diagnostic reads the static `_n.png` paired with the spawned cow's actual albedo `SimpleTexture`. The integration harness supplies authored +X tangent-space normal maps through an original resource pack. Only a cow fragment that samples the authored map emits magenta; a missing-map fallback emits red, and the terrain remains green.

The fixture exercises entity `normals` sampler adaptation, live albedo texture lookup, lazy normal texture upload, and descriptor binding. It does not cover downloaded player skins, animated maps, or a complete shaderpack.
