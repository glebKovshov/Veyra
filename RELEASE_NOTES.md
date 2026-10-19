# Veyra 0.2.0-beta.7 ? experimental prerelease

This release extends Veyra's supported Iris shader-pack subset on VulkanMod. It is not a stable release and does not claim full Iris parity. Active incompatibilities remain in every tested third-party pack; none is certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.7+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.6

- Add Iris cloud inputs for `colortex0` scene-color sampling and the existing Vulkan-backed `noisetex`, including legacy `texture2DLod` reads lowered to Vulkan GLSL `textureLod`. Veyra copies the completed main scene into a separate Vulkan image before drawing clouds, avoiding feedback from the active output attachment.
- Strip unused cloud sampler declarations before validation. An unread `colortex0` declaration in a shared uniforms file no longer blocks the cloud pass.
- Add `examples/cloud-colortex0` and a Lavapipe graphics fixture that checks scene capture, legacy `noisetex` LOD sampling, and the blue-sky magenta signal.
- Keep other unsupported cloud samplers, texture coordinates, and cloud inputs explicit.

## Verification

- Local JDK 21 `test validateShaders build packageExamplePacks` passes: 187 JUnit tests, 40 example shaders processed, and 37 compiled directly to Vulkan SPIR-V. The installable JAR passes the distributable-content verifier.
- The [beta.7 Release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39331562077) passed the pinned shaderpack source audit, all 13 synthetic Lavapipe graphics fixtures, build/tests, and JAR verification before publication. The cloud fixture produced 52,412 magenta pixels with shaders enabled and 0 after disabling them, with no runtime Vulkan validation errors. The source audit is not a full-pack compilation or visual test.
- Third-party pack visuals on a physical GPU, resizing during a live cloud pass, and long-session resource stress have not been validated.

## Known limits

- Cloud fragments can sample scene color through `colortex0` and the documented aliases, plus `noisetex`; supported sampling calls are `texture`, `texture2D`, `textureLod`, `texture2DLod`, and `textureSize`. Other cloud samplers, texture coordinates, and cloud fragment custom uniforms remain unsupported. The snapshot requires a transferable Vulkan main color image and performs a full-frame copy when the cloud pass uses scene color.
- Complementary terrain and water still require the unsupported `lmCoord` input; selected profiles use compute stages Veyra does not execute. Complementary entity, block, and hand programs still require `currentRenderedItemId`.
- BSL still has unsupported entity color/bridge inputs and cloud texture sampling. Sildur still needs `colortex4` in non-terrain geometry and defined water attachment-feedback behavior; its cloud texture sampler is unsupported.
- Iris' built-in legacy block ID table is not loaded when a pack has no `block.properties`, so those block-entity identifiers remain unmapped.
- Normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and real-pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
