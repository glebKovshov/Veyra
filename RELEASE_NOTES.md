# Veyra 0.2 development — not a stable release

Target: Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.

This branch is not ready for the requested main-branch full-Iris release. Do not publish these development notes as a claim of broad shaderpack compatibility. Existing 0.1.1 release assets and notes remain attached to that release.

Implemented changes include persistent shader options/profiles and a Minecraft configuration screen, conditional GLSL preprocessing, dimension-specific programs and enable conditions, separate prepare/deferred/composite boundaries, pipeline/cache lifecycle fixes and initialized depth history. See CHANGELOG.md for details.

Validation at branch commit `340db0ca80010d42591383b13e12d76463467048`: 74 regression tests pass. Full Fabric Loom builds, shaderc example checks and distributable-content verification pass in GitHub Actions ([push build](https://github.com/glebKovshov/Veyra/actions/runs/39166689216), [PR build](https://github.com/glebKovshov/Veyra/actions/runs/39166691696)). Native checks preprocess 19 bundled examples and compile 16 directly to Vulkan SPIR-V, plus four conditional-include checks. The pinned source-audit workflow checked four popular packs at defaults in three dimensions and all 21 declared profiles across three dimensions (75 total cases, 5,178 stage-source preprocesses). Only 18 of 717 geometry fragment/configuration adapter attempts were accepted. See COMPATIBILITY.md for exact pack blockers and the source-only limits.

**Known blockers:** arbitrary geometry vertex stages/attributes, non-terrain geometry MRT, unsupported geometry samplers and custom uniforms, compute/tessellation execution and additional Iris stages. Some Complementary profile/dimension cases require unsupported compute. The [Lavapipe client smoke run](https://github.com/glebKovshov/Veyra/actions/runs/39166399772) passes Veyra's synthetic MRT/composite fixture (222,934 active magenta pixels, 0 with shaders disabled) with no runtime Vulkan validation errors; it does not render the popular packs. The run still reports 10,031 Vulkan child-object diagnostics during process teardown. No physical GPU memory stress test or FPS benchmark has been performed. See COMPATIBILITY.md and ARCHITECTURE.md for exact evidence and remaining work.

The CI artifact is a development build of the supported subset, not evidence of the requested complete mod. Main-branch merging and a stable release remain pending implementation and runtime verification of those blockers.


Continuation 2026-10-19: local JDK 21 rebuild passes 92 regression tests, 21 source preprocesses and 18 direct SPIR-V compilations. JAR content verification passes. Recursive include work is bounded and the complete source audit now supports Windows argument files. Release publication now depends on source-audit and graphics-smoke workflows. These additions do not resolve the popular-pack or physical-GPU blockers above.
