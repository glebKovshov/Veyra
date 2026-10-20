# Veyra 0.2.0-beta.25+mc1.21.11 — experimental prerelease

Beta.25 adds a supported subset of armor-glint vertex execution for Minecraft 1.21.11 with VulkanMod 0.6.8. It remains an experimental subset and does not provide full Iris compatibility or establish that a complete third-party shaderpack is playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.25+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; shader pack archives are not modified.

## Changes since beta.24

- Execute a supported subset of `gbuffers_armor_glint.vsh` after Minecraft's glint vertex setup and link checked color, UV, and fog-distance outputs to the adapted fragment stage.
- Bridge legacy position/UV aliases, the glint texture matrix, Iris fog fields, and `noisetex`. Minecraft's native `POSITION_TEX` glint format supplies no color input, so legacy `gl_Color` maps to white.
- Map fragment sampler aliases `texture`, `gtexture`, and Iris `tex` to Minecraft's `Sampler0`. Prune unreachable helpers and unused uniform/sampler declarations before capability checks while retaining errors for active unsupported reads.
- Add adapter regressions, shaderc SPIR-V fixtures, an example pack, and an enchanted-book Lavapipe fixture.

## Validation and known limits

PR [#62](https://github.com/glebKovshov/Veyra/pull/62) merged to `main` as `df3df827743adf9a978008c59fe9812cd16a699d`. The final PR head passed Build, pinned source audit, and all **25/25** Lavapipe graphics fixtures. The glint fixture read back 313 bright green pixels with the pack active and 0 after disabling it; CI found no runtime Vulkan validation errors before shutdown. Process teardown diagnostics remain.

The pinned audit covers 4 archives / 75 profile-dimension cases / 5,178 stages: 705 geometry fragments accepted and 12 rejected. Complementary glint passes source adaptation. The audited packs still encounter unsupported `gbuffers_basic.vsh`, Sildur attachment 4 and `LightC`, and selected Complementary `shadowcomp.csh` compute programs. Every audit record has `visualValidation=false`. No complete third-party pack or physical-GPU visual validation is claimed; the stable goal remains incomplete.