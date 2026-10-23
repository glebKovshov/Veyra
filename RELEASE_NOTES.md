# Veyra 0.2.0-beta.36+mc1.21.11 - experimental prerelease

Beta.36 adds read-only custom-image sampling to the vertex stages of `gbuffers_basic` and `gbuffers_line`. The shared sampler declarations are prepared before native vertex adaptation, and the native descriptor layout includes the image samplers used by both vertex and fragment stages. Storage-image writes through these basic/line render pipelines remain explicitly unsupported.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.36+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The final local JDK 21 gate passed **554 tests** with no failures, errors, or skips; it validated **102 example shaders**, compiled **118 shader stages to Vulkan SPIR-V**, built the mod, packaged example packs, and compiled graphics-smoke sources. The pinned source-only audit passed four archives across 12 default and 63 declared-profile cases: **864 geometry-fragment programs accepted / 0 rejected** and **18/18 active compute stages compiled**. Each source-audit row retains `visualValidation: false`.

The hosted `basic-custom-image` Lavapipe fixture sampled the same image in the basic vertex and fragment stages and read **471 active green pixels / 0 after disabling the pack**, with **0 runtime Vulkan validation errors**. Twenty process-teardown diagnostics were retained in the CI artifact. The line-stage example is covered by capability/interface regression checks; it has no line-specific GPU readback. These are named synthetic fixtures, not third-party shaderpack or natural-world visual certification. The release workflow runs the full Lavapipe matrix, source audit, versioned build/tests, and distributable verifier before publishing.

Veyra remains an experimental Iris-compatible subset. Do not infer complete shaderpack support or stable Iris parity from these results. See `COMPATIBILITY.md` and `ARCHITECTURE.md` for measured evidence and unresolved work.
