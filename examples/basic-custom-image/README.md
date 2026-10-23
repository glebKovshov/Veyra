# Basic custom-image fixture

The shadow vertex stage writes voxel 5 to `image.voxel_img`. The `gbuffers_basic` vertex and leash fragment stages both sample the shared image through `voxel_sampler`; the fragment writes green to the scene and `colortex6` only when both stages read 5. Disabling the pack removes the green signal. This exercises custom-image descriptors in both stages of a native Minecraft `RenderPipeline`. The example also supplies a line vertex stage that reuses the basic fragment stage; capability validation checks its custom-image sampler and line vertex interface.
