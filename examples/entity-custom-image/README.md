# Entity custom-image fixture

The shadow vertex stage writes voxel value 5 to `image.voxel_img`. The cow's
`gbuffers_entities` fragment samples the same image through `voxel_sampler`
and turns green only when the shared value is 5. Disabling the pack removes
the green signal. This exercises the native entity `RenderPipeline` descriptor
path and its shared-image binding.
