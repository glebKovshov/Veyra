# Veyra 0.2.0-beta.37+mc1.21.11 - experimental prerelease

Beta.37 adds Iris `setup` and pre-shadow `begin` compute programs, pre-shadow `begin.fsh` passes, and compute programs that use `bufferObject.N` without a custom storage image. Setup and begin writers preserve their SSBO contents for later passes and frames. Begin fragment color targets are cleared once per frame, before the begin pass, so its output can feed later shader programs.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.37+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The local JDK 21 gate passed **568 tests** with no failures, errors, or skips; it validated **102 example shader sources**, compiled **118 stages to Vulkan SPIR-V**, built the mod, packaged example packs, and compiled graphics-smoke sources. Separate RTX 3070 runs of the SSBO-only compute, setup, begin-compute, and begin-fragment fixtures each produced **230,400 active / 0 disabled marker pixels**. Setup-on-resize was not separately exercised on the physical GPU. Khronos validation was not enabled in these Windows runs; the client logs contained no VUID or device-loss markers.

The local pinned source-only audit passed for four archives across 12 defaults and 63 declared-profile/dimension cases: **864 geometry fragments accepted / 0 rejected** and **18/18 active compute stages compiled**. All 75 report rows retain `visualValidation: false`; the audit does not establish runtime or visual compatibility.

The release workflow requires a fresh pinned source audit, the full Lavapipe graphics matrix, versioned build/tests, and distributable verification before publishing. These checks validate selected source paths and synthetic render fixtures, not complete third-party shaderpacks or natural-world visuals. Pinned source-audit rows explicitly retain `visualValidation: false`.

Veyra remains an experimental Iris-compatible subset. Compute support covers the documented setup, begin, shadow-compute, and post-processing families and selected SSBO/image semantics; other Iris features remain unsupported or unverified. Do not infer complete shaderpack support or stable Iris parity from these results. See `COMPATIBILITY.md` and `ARCHITECTURE.md` for the measured matrix and backend gaps.
