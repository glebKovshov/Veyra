# Normal atlas readback

This original diagnostic loads a separate resource pack with static _n.png maps for the block sprites in the flat test world. Each map encodes a unit tangent-space normal pointing along +X. The terrain shader samples mip level 2 and emits magenta only when that normal data and at least three mip levels are present; the Iris flat-normal fallback emits green. The graphics-smoke gate then checks that the magenta signal disappears when shader rendering is disabled.

The fixture covers static terrain block-atlas packing, descriptor binding, vector-aware mip uploads, and block-atlas texture reload setup. It does not test entity normal maps, animated maps, or a complete shaderpack.
