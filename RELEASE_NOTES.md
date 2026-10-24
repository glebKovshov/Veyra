# Veyra 0.2.0-beta.38+mc1.21.11 - experimental prerelease

Beta.38 adds pre-shadow `shadow.csh` and consecutive letter components, fixes compute UBO uniform suppliers, and matches Iris' 1x1 extent for setup render-relative dispatch. Setup and pre-shadow compute share the existing custom-image and `bufferObject.N` resources; setup/begin results remain available to later passes and frames.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.38+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The forced versioned local beta.38 JDK 21 gate passed 573 tests without failures, errors, or skips, 102 example shader sources / 118 direct Vulkan SPIR-V compilations, build, graphics-smoke compilation, and example-pack packaging. Five Python tooling tests passed. The source-only audit accepted 864 geometry fragments and compiled 18/18 active compute stages across four archives / 75 default/profile/dimension cases. All audit rows retain `visualValidation=false`.

On RTX 3070, the pre-shadow fixture verified repeated shadow -> letter component -> begin -> composite visibility and 32x32 shadow sizing (230,400 active / 0 disabled marker pixels). The setup fixture verified an actual 640x360 -> 800x450 window resize, one setup execution at each boundary, updated compute viewport uniforms and no excess invocations (360,000 / 0 pixels). The isolated final clients exited cleanly. One preceding Windows run passed readback but exited during native shutdown with 0xC0000409; its cause is unproven. Khronos validation was not enabled on Windows.

A fresh beta.38 RTX 3070 Complementary Reimagined r5.9.3 COMPLEMENTARY run passed resize, Nether/return, disable/re-enable and resource reload (326,462 non-black pixels / 55 color buckets). Its 810,549,248-byte storage-buffer owner was released and recreated; no shader-pack buffers remained before allocator destruction. This generated-scene lifecycle check is not whole-pack visual certification.

The release workflow must pass a fresh pinned source audit, full Lavapipe graphics matrix, versioned build/tests and distributable verification before publication. Both feature PRs passed their own Build, source audit and all 54 hosted Graphics jobs before merging. Those results do not replace the versioned release gate. Hosted setup readback had zero runtime validation errors; 20 process-teardown diagnostics were preserved separately. Writable shadow images require the corresponding device features; source audit does not prove support on another GPU.

Known Complementary runtime gaps include sign-text `upVec` inputs and `gaux2` sampling in the textured particle fallback; the corresponding shader overrides are skipped with warnings.

Veyra remains an experimental Iris-compatible subset. The pre-shadow family supports declared custom images and allocated SSBOs through the existing compute interface; arbitrary compute sampler inputs, geometry/tessellation families and complete third-party/natural-world visuals remain unsupported or unverified. See COMPATIBILITY.md and ARCHITECTURE.md for the measured scope. This prerelease does not establish stable Iris parity.
