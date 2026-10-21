# Composite custom-image consumer

This diagnostic uses the production shaderpack lifecycle. Shadow terrain writes the value 5 to `voxel_img`, `shadowcomp.csh` adds 37 into the shared `floodfill_img`, and `composite.fsh` samples that same 3D image after scene rendering. The full-screen composite becomes magenta only when it reads 42. Disabling the pack removes the magenta signal.
