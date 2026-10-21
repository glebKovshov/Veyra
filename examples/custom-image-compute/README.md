# Shared shadow and compute images

Original diagnostic using the production shaderpack lifecycle: terrain shadow vertices atomically write a voxel, shadowcomp samples that image and writes a second volume, then terrain samples the compute output. Magenta requires the value 42 from the complete chain; clearing, missing vertex execution, skipped dispatch or separate image allocations cannot produce it. Images are only 4x4x4 texels. Disabling the pack must remove the signal and retire its resources.
