# Veyra 0.2 development — not a stable release

Target: Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.

This branch is not ready for the requested main-branch full-Iris release. Do not publish these development notes as a claim of broad shaderpack compatibility. Existing 0.1.1 release assets and notes remain attached to that release.

Implemented changes include persistent shader options/profiles and a Minecraft configuration screen, conditional GLSL preprocessing, dimension-specific programs and enable conditions, separate prepare/deferred/composite boundaries, pipeline/cache lifecycle fixes and initialized depth history. See CHANGELOG.md for details.

Validation: 57 regression tests pass. Full Fabric Loom builds, shaderc example checks and distributable-content verification pass in GitHub Actions. Native checks preprocess 18 bundled examples and compile 15 directly to Vulkan SPIR-V, plus four conditional-include checks. The four requested popular packs were source-audited at defaults in three dimensions; all 21 declared profiles were also checked across those dimensions (63 profile cases, 4,429 preprocessed stage sources).

**Known blockers:** arbitrary geometry vertex stages/attributes, native simultaneous geometry MRT, missing sampler/custom-uniform interfaces and additional-stage execution. Some high Complementary profiles require compute. All tested popular-pack geometry fragments still fail the restricted adapters. No Minecraft client, visual rendering, Vulkan validation-layer run, GPU memory stress test or FPS benchmark has been performed. See COMPATIBILITY.md and ARCHITECTURE.md for exact evidence and remaining work.

The CI artifact is a development build of the supported subset, not evidence of the requested complete mod. Main-branch merging and a stable release remain pending implementation and runtime verification of those blockers.
