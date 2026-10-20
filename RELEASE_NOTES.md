# Veyra 0.2.0-beta.26+mc1.21.11 — experimental prerelease

Beta.26 batches limited custom vertex execution for basic/line, beacon, block-entity, entity, and first-person hand programs, and fixes a cloud-renderer Mixin crash in beta.25. It remains an experimental subset: it does not provide full Iris compatibility or establish that a complete third-party shaderpack is playable.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install veyra-0.2.0-beta.26+mc1.21.11.jar in the instance mods folder alongside VulkanMod. Keep only one Veyra JAR installed; do not install the sources JAR.
- Optional example packs can be extracted into shaderpacks. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under veyra/options/; shader pack archives are not modified.

## Changes since beta.25

- Fix the beta.25 CloudRenderer crash by allowing Mixin to remap the Tesselator.begin selector to the method descriptor in VulkanMod.
- Add supported custom vertex execution for basic/leash, line, beacon beam, block-entity, entity, and first-person hand shader programs. Pack varyings are linked with type, interpolation, and location checks after Minecraft's native vertex setup.
- Add beacon fragment MRT outputs and the block/entity/hand vertex inputs supported by Minecraft's NEW_ENTITY format. Hand depth scaling is preserved and entity vertex noisetex is available.
- Reject unsupported reads of attributes that Minecraft does not provide, including mc_Entity, mc_midTexCoord, at_tangent, and at_midBlock, with a named diagnostic.
- Add targeted shader compiler, example-pack, and real graphics fixtures for these paths.

## Validation and known limits

PR #67 merged to main as 27295d2d9a5f298a5c7558c7ce595229a1a41bca. The final head passed Build 39515439644, pinned source audit 39515439580, and all 30/30 Lavapipe graphics fixtures in run 39515439750. One runner initially failed before launching Minecraft while downloading assets; its isolated retry launched the client, verified frame readback, and passed. The local JDK 21 build passed 299 tests; 79 example shader sources were processed and 78 directly compiled to Vulkan SPIR-V.

On NVIDIA GeForce RTX 3070, separate synthetic fixtures measured active/disabled magenta pixels of 18,532/0 for entities, 2,428/0 for block entities, and 47,282/0 for first-person hand. These checks validate specific adapter paths, not complete shaderpack visuals.

The pinned source audit covers 4 archives, 75 profile/dimension cases, and 5,178 stages: 709 geometry fragments accepted / 8 rejected. Every record has visualValidation=false. Remaining first whole-pack blockers include:

| Pack | Current blockers |
| --- | --- |
| Complementary Reimagined / Unbound r5.9.3 | gbuffers_damagedblock.vsh; selected profiles also declare unsupported shadowcomp.csh compute |
| BSL 10.1.8 | gbuffers_block.vsh requires mc_Entity, which Minecraft's NEW_ENTITY format does not provide |
| Sildur's Vibrant 2.02 Extreme | shadowData in block-translucent programs and unsupported gbuffers_skybasic.vsh |

The full pinned profile/dimension table is in COMPATIBILITY.md. No complete third-party shaderpack has been visually validated; the stable-release goal remains incomplete.
