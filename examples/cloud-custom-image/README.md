# Cloud custom image fixture

Uses the production lifecycle: shadow terrain samples the block atlas/lightmap and writes voxel 5, `shadowcomp` consumes it and fills the shared 3D image with 42, terrain stays green, and `gbuffers_clouds` emits magenta only when its `usampler3D` reads voxel 5 from that same image and `renderStage` is `MC_RENDER_STAGE_CLOUDS`. With the pack disabled, the cloud sampler and signal disappear. A passing GPU run therefore covers cloud `renderStage` binding and Pipeline.Builder descriptor binding in addition to the terrain/compute chain.
