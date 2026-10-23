# Specular atlas readback

This original diagnostic requires a resource pack supplying magenta-valued material PNGs for visible block sprites (for example `textures/block/grass_block_top_s.png`). It samples specular mip level 2 and checks that the view exposes at least three levels. Magenta means actual resource data matched; missing-material zero data produces green. The CI preparation script creates that original resource pack separately. No third-party resource pack is redistributed.

Static `_s.png` atlases use the live albedo sprite coordinates/padding and data mipmaps (including LabPBR channel categories when declared). The separate `normal-atlas-animated` fixture covers animated specular and normal atlas regions in sync with their base sprite. This is not a general PBR shader or full shaderpack-compatibility certification.
