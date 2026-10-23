# Basic custom-image fixture

The shadow vertex stage writes voxel 5 to `image.voxel_img`. The leash fragment in `gbuffers_basic` samples the same image through `voxel_sampler` and writes green to the scene and `colortex6` only when the value is 5. Disabling the pack removes the green signal. This exercises the native Minecraft `RenderPipeline` descriptor path.
