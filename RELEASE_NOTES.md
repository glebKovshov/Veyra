# Veyra 0.2.0-beta.44+mc1.21.11 - experimental prerelease

Beta.44 fixes profile-specific geometry output mapping. Complementary Reimagined r5.9.3 HIGH declares a fallback DRAWBUFFERS:06 mapping and a later conditional DRAWBUFFERS:064 mapping; Veyra now uses the active mapping so the third fragment output reaches colortex4 for downstream deferred passes.

## Changes

- Resolve geometry fragment outputs with the shared active RENDERTARGETS / DRAWBUFFERS parser.
- Ignore directive-shaped text inside line comments and cover profile-specific mappings with a regression test.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install veyra-0.2.0-beta.44+mc1.21.11.jar beside VulkanMod. Keep only one Veyra JAR in the instance; do not install the sources JAR.
- Put shaderpack folders or ZIPs in shaderpacks. Press F6 to select a pack, then Configure / Apply to edit supported options and profiles.

## Validation and known limits

The JDK 21 gate passed 584 tests without failures, errors or skips; it validated 104 example shaders and compiled 122 direct Vulkan SPIR-V stages, and passed shader validation, build, graphics-smoke compilation and example-pack packaging. The five Python CI audit tests passed.

The pinned source audit covered four shaderpacks, 12 defaults and 63 profiles. It accepted 921 geometry fragments, rejected 18 gbuffers_textured fragments for the existing unsupported gaux2 sampler, and compiled all 18 active compute stages. The audit is source-only and each result has visualValidation=false.

The RTX 3070 Complementary HIGH lifecycle smoke passed resize, Nether/Overworld transitions, disable/re-enable and resource reload. It reported 359,998 non-black pixels and 109 color buckets with no smoke error. This is generated-scene evidence, not natural-world or whole-pack visual certification.

A matched Iris HIGH capture from beta.43 showed Veyra darker overall with a different sign appearance. Beta.44 does not claim to fix that visual difference. Veyra remains an experimental Iris-compatible subset, not a stable Iris-parity release. See COMPATIBILITY.md and ARCHITECTURE.md for measured scope and unsupported stages.
