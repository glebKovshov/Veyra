# Veyra 0.2.0-beta.1 — experimental supported subset

This is a **beta prerelease**, not Iris parity and not a stable release. Stable 0.1.1 remains available. Complementary, BSL and Sildur's still have known source incompatibilities and are **not** certified playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Put `veyra-0.2.0-beta.1+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the build uses Fabric API 0.140.0+1.21.11.
- Extract the optional example-pack archive into `shaderpacks/`, or add a supported pack folder/ZIP. Press **F6**, select the pack and use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since 0.2.0-alpha.1

- `gbuffers_water` can read copied opaque depth (`depthtex1`) and up to six auxiliary `colortex1..15` buffers (plus legacy aliases), using the current ping-pong side after deferred.
- Static resource-pack `_s.png` specular maps for terrain, shadow and entity programs (linear/LabPBR mipmaps).
- Completed shadow color (`shadowcolor0` / `shadowcolor`) and a separate pre-translucent `shadowtex1` for terrain, entity and post programs.
- Fixed real native defects: optional descriptor binding gaps, borrowed main-depth table binding, swapped usage/texture-ID arguments in borrowed depth views, final shadow depth layout after entity passes, quadratic declaration scans on long blank regions.
- Shutdown ownership trace: before allocator destruction no Veyra-named buffers, images, pipelines or pending retirements remain; the object registry matches a vanilla VulkanMod baseline.

See CHANGELOG.md for the full history.

## Evidence and limitations

- Local JDK 21 and CI: **139 JUnit tests**, 0 failures/errors/skips; shaderc **38** example sources preprocessed / **35** direct SPIR-V compilations; JAR verifier passes.
- Lavapipe (software Vulkan) graphics CI: **11 fixtures** (MRT, water depth/color, specular atlas/entity, shadow color terrain/entity, separate shadow depth water/entity, post shadow chain, vanilla baseline) pass with **0 runtime validation errors**. These are original synthetic fixtures, not popular packs.
- Whole-process teardown reports ~10,031 VulkanMod child-object diagnostics **also with no shader pack selected** (vanilla baseline); with Veyra fixtures the count differs by a few objects, none Veyra-named. Clean VulkanMod teardown is not claimed.
- Pinned source audit: 75 configurations, 5,178 sources, 18 accepted / 699 rejected geometry cases (adapter coverage, not in-game rendering).

| Pack | Source audit | Visual compatibility |
| --- | --- | --- |
| Complementary Reimagined r5.9.3 | rejected (blockEntityId etc.) | Not verified |
| Complementary Unbound r5.9.3 | rejected (blockEntityId etc.) | Not verified |
| BSL 10.1.8 | partly accepted; entityId/blockEntityId/shadowFade missing | Not verified |
| Sildur's Vibrant 2.02 Extreme | rejected (color feedback, geometry inputs) | Not verified |

Not implemented: per-draw entityId/blockEntityId, shadowFade, arbitrary geometry vertex stages, non-terrain MRT, normal maps, animated specular, compute/tessellation execution. Unsupported active features produce diagnostics instead of fake output. No physical GPU or native UI test was performed for this release.
