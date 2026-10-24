# Veyra 0.2.0-beta.46+mc1.21.11 - experimental prerelease

Beta.46 fixes shaderpack shadow depth handling while retaining Veyra's VulkanMod-compatible shadow view and culling. It also avoids runaway scans when adapting large entity vertex shaders.

## Changes

- Read Iris shadow near/far planes and sun/moon shadow angle; keep Iris uniform depth and Vulkan draw depth separate.
- Use vanilla-compatible clip bounds for shadow frustum culling.
- Check mc_Entity layouts one declaration at a time and retain clear errors for unsupported layouts.
- Scan the complete shadow map in the shadow-color graphics fixture.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install veyra-0.2.0-beta.46+mc1.21.11.jar beside VulkanMod. Keep only one Veyra JAR in the instance; do not install the sources JAR.
- Put shaderpack folders or ZIPs in shaderpacks. Press F6 to select a pack, then Configure / Apply to edit supported options and profiles.

## Validation and known limits

The JDK 21 gate passed 589 tests without failures, errors or skips; it validated 104 example shaders and compiled 122 direct Vulkan SPIR-V stages. Shader validation, build, graphics-smoke compilation and example-pack packaging passed.

On an NVIDIA RTX 3070, the pinned Complementary Reimagined r5.9.3 HIGH lifecycle smoke passed with 360,000 non-black pixels / 108 color buckets. It covered resize, Nether/Overworld transitions, shaderpack disable/re-enable and a real resource reload. The shadow-color-terrain fixture passed with a full-map scan. These are generated-scene checks for tested profiles, not whole-pack certification.

Full Iris visual parity, natural-world comparison and whole-pack certification remain unverified. Veyra remains an experimental Iris-compatible subset, not a stable Iris-parity release. See COMPATIBILITY.md and ARCHITECTURE.md for measured scope and unsupported stages.
