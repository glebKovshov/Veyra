# Changelog

All notable changes to Veyra are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html) with a `+mc<version>` build suffix for the target Minecraft version. All 0.2.0 releases target Minecraft 1.21.11 and VulkanMod 0.6.8 and are experimental prereleases.

Current shader pack compatibility is tracked in [docs/compatibility.md](docs/compatibility.md), and current test results in [docs/validation.md](docs/validation.md).

## [Unreleased]

### Changed

- Consolidated the documentation: README focuses on installation and usage, reference material lives in `docs/`, and this changelog is the single release history. Removed the internal `WORK_SUMMARY.md` handoff log.

### Fixed

- Corrected the Sildur's Vibrant row in the compatibility table, which reported totals across three dimensions (3/15) as per-dimension results (1/5).

## [0.2.0-beta.23] - 2026-10-20

### Changed

- An active custom `gbuffers_*.vsh` stage for any geometry program other than clouds is now rejected with an explicit error when its fragment program exists. Previously Veyra silently ran VulkanMod's built-in vertex shader instead. Disabled programs and the cloud and post-processing vertex stages are unaffected.

## [0.2.0-beta.22] - 2026-10-20

### Added

- Water and entity-family geometry can sample up to six auxiliary `colortex1`–`colortex15` buffers.
- When a sampled buffer is also a draw attachment, Veyra copies it to a separate snapshot before drawing, so the original stays writable.
- `entity-colortex-feedback` example and graphics fixture.

### Fixed

- Resuming a render pass on the same framebuffer after a feedback snapshot.

## [0.2.0-beta.21] - 2026-10-20

### Added

- Cloud vertex stages can use supported legacy fog fields, `gl_ModelViewMatrix`, lowercase `texcoord`, and `dist`.
- `cloud-model-view` example and graphics fixture.

### Fixed

- Cloud vertex helper functions that are unreachable from `main()` no longer cause false unsupported-input errors.

## [0.2.0-beta.20] - 2026-10-20

### Added

- Cloud fragments can sample Minecraft's cloud texture.
- Cloud vertex stages can request per-quad UVs and flat face normals; BSL `sunVec`, `upVec`, and `eastVec` are bridged.
- `cloud-texture-inputs` example and graphics fixture.

## [0.2.0-beta.19] - 2026-10-20

### Added

- Terrain `dist` and BSL `recolor`.
- Water `sunVec`, `upVec`, and `eastVec`, and BSL `vTexCoord` and `vTexCoordAM`.

## [0.2.0-beta.18] - 2026-10-20

### Added

- Legacy `gl_Fog.color`, `gl_Fog.start`, `gl_Fog.end`, and `gl_Fog.scale`, bridged through Minecraft's fog uniform buffer.

### Changed

- `gl_Fog.density` is rejected explicitly, because no validated runtime value exists.

## [0.2.0-beta.17] - 2026-10-20

### Added

- Terrain `midCoord`, with the live texture matrix applied; `signMidCoordPos` and `absMidCoordPos` now use the same transformed coordinates.

### Fixed

- An uninitialized VulkanMod texture matrix collapsed terrain and specular UVs to zero; it is now treated as identity.

## [0.2.0-beta.16] - 2026-10-20

### Added

- Terrain `absMidCoordPos`.

## [0.2.0-beta.15] - 2026-10-20

### Added

- Terrain `mat` and `mc_Entity` from `block.properties`, including the fluid marker.
- Terrain sections are rebuilt when a newly selected pack changes the block mappings.
- `terrain-block-id` and `water-block-id` examples and graphics fixtures.

### Fixed

- Repeated block ID entries, comma-separated state values, and direct block and state selectors taking priority over tags (Iris 1.8+ behavior).

## [0.2.0-beta.14] - 2026-10-20

### Added

- Native multiple render targets for entity, first-person hand, block entity, and sign text programs.
- `entity-mrt` graphics fixture.

## [0.2.0-beta.13] - 2026-10-20

### Added

- Iris `isMainHand` for first-person hand programs, with separate main-hand and offhand pipelines.
- BSL `sunVec`, `upVec`, and `eastVec`.

### Fixed

- Comma-separated legacy `varying` declarations.

## [0.2.0-beta.12] - 2026-10-20

### Added

- Iris `entityColor` mapped to Minecraft's overlay color where the pipeline provides it.
- Entity-family `lmCoord` spelling.

### Fixed

- Entity shaders declaring `viewPos` as a local variable were rejected.
- Line-anchored uniform scans could match across lines.

## [0.2.0-beta.11] - 2026-10-20

### Added

- Iris `currentRenderedItemId` from `item.properties`, for entity, block entity, first-person hand, and shadow paths.
- `current-rendered-item-id` example and graphics fixture.

## [0.2.0-beta.10] - 2026-10-20

### Added

- Per-quad terrain midpoint UVs in VulkanMod's terrain vertex format, and `signMidCoordPos`.
- Terrain `vertexPos`, `playerPos`, and `glColorRaw`.
- `geometry-mid-texcoord` example and graphics fixture.

## [0.2.0-beta.9] - 2026-10-20

### Added

- Terrain `viewPos`.

## [0.2.0-beta.8] - 2026-10-20

### Added

- Terrain `lmCoord` and `lightMapCoord` aliases and BSL `color`.

### Changed

- Terrain adapter errors name the unsupported input.

## [0.2.0-beta.7] - 2026-10-20

### Added

- Cloud fragments can sample a pre-cloud copy of scene color through `colortex0`, and `noisetex`, including legacy `texture2DLod`.
- `cloud-colortex0` example and graphics fixture.

### Fixed

- An unused `colortex0` declaration in a shared include no longer blocks cloud programs.

## [0.2.0-beta.6] - 2026-10-19

### Added

- Iris dimension, player, celestial, and hardcoded custom uniforms for terrain, entity-family, and post programs, including the hardcoded `velocity`.
- Iris custom-uniform functions: `smooth()` with half-lives in tenths of a second, floored `fmod`, `edge`, `log(base, x)`, `log10`, `exp10`, `random`, and `randomInt`.

### Changed

- Custom uniforms that reference an unregistered `BIOME_*` constant stay 0 with a warning, as in Iris, instead of rejecting the program.

### Fixed

- After switching packs, a pack that overrides `shadowFade` left later packs reading 0.

## [0.2.0-beta.5] - 2026-10-19

### Added

- `gbuffers_clouds.vsh` support for `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix`, and `ftransform()`.

## [0.2.0-beta.4] - 2026-10-19

### Added

- `entity.properties` and `block.properties` with option preprocessing, block state predicates, and block tags; mapped `entityId` and `blockEntityId`, with `-1` for unmapped IDs.

## [0.2.0-beta.3] - 2026-10-19

### Added

- Cloud `fogColor`, `fogStart`, and `fogEnd`.

## [0.2.0-beta.2] - 2026-10-19

### Added

- Scalar custom uniforms (`uniform.*`, `variable.*`) from `shaders.properties`.
- Iris `shadowFade`; a pack's own definition takes precedence.
- Iris near/far, held item and light, biome precipitation, player mood and look, sky color, atlas size, lightning bolt position, and darkness uniforms; relative eye position for post programs.

### Fixed

- `ivec2` post uniform blocks.
- Handling of Iris-unbound entity identifiers and `entityColor`.

## [0.2.0-beta.1] - 2026-10-19

This release also contains the changes from `0.2.0-alpha.1`.

### Added

- Shader pack options: `#define` and `const` options, allowed values, profiles with inheritance and program toggles, sliders, nested screens, labels, and column settings. Overrides are saved per pack and atomically.
- GLSL macro and conditional preprocessing with JCPP before interface adaptation.
- Dimension-specific programs (`world0`, `world-1`, `world1`) and `program.world0/<name>.enabled` conditions.
- Separate scheduling of `prepare` (after shadows), `deferred` (before translucent terrain), and `composite`/`final`.
- Native terrain MRT, output location mapping, and per-program and per-buffer blend overrides.
- Shadow inputs: `shadowtex0` for entity-family programs; separate pre-translucent `shadowtex1`; `shadowcolor0`/`shadowcolor` for terrain, entity-family, and post programs.
- `depthtex1` and auxiliary `colortex` inputs in `gbuffers_water`.
- Static `_s.png` specular maps for terrain, shadow, and entity textures, with linear and LabPBR mipmaps.
- Generated `noisetex` with `noiseTextureResolution`.
- `eyeBrightness`, `eyeBrightnessSmooth`, `moonPhase`, `bedrockLevel`, `cloudHeight`, and `endFlashIntensity` uniforms.
- Entity `color` varying, legacy `tex` sampler, and `textureSize` on bridged samplers.
- Explicit errors for unsupported compute, geometry, tessellation, and `begin` stages.
- Graphics smoke tests in a real client on software Vulkan, and a checksum-pinned source audit of four public shader packs. Both gate releases.

### Changed

- Unused sampler declarations are removed before validation.
- `sampler2DShadow` helper parameters in terrain shaders use Veyra's software depth comparison.
- Source and configuration caches are scoped to a pack load; changing options recompiles only when values change.
- GitHub Actions use Node 24 actions on Ubuntu 24.04 with Gradle's basic cache provider.

### Fixed

- Swapped usage and native ID arguments in borrowed depth views could bind a stale texture.
- First-frame depth history was not cleared before sampling.
- Shadow depth layout and matrices after entity passes and shadow translucency.
- Image views destroyed while still referenced by submitted descriptors.
- Viewport and scissor lost when switching geometry targets.
- Swapchain image usage flags and the ordering of sampled image transitions.
- Disabled programs ran because their short name had no condition.
- Unbounded declaration scans over long blank regions and unbounded include expansion.
- Discovered packs disappeared when restoring a broken active pack failed.
- Optional terrain descriptor gaps when shadow or noise samplers are absent.

## [0.1.1] - 2026-10-18

### Fixed

- Include comments, guarded recursion, and inactive missing includes.
- Pack refresh and reload, selection persistence, and resource cleanup.
- Composite passes now run when the GUI is hidden.

### Added

- Regression tests and shaderc checks for the example packs.

[Unreleased]: https://github.com/glebKovshov/Veyra/compare/v0.2.0-beta.23%2Bmc1.21.11...HEAD
[0.2.0-beta.23]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.23%2Bmc1.21.11
[0.2.0-beta.22]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.22%2Bmc1.21.11
[0.2.0-beta.21]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.21%2Bmc1.21.11
[0.2.0-beta.20]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.20%2Bmc1.21.11
[0.2.0-beta.19]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.19%2Bmc1.21.11
[0.2.0-beta.18]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.18%2Bmc1.21.11
[0.2.0-beta.17]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.17%2Bmc1.21.11
[0.2.0-beta.16]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.16%2Bmc1.21.11
[0.2.0-beta.15]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.15%2Bmc1.21.11
[0.2.0-beta.14]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.14%2Bmc1.21.11
[0.2.0-beta.13]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.13%2Bmc1.21.11
[0.2.0-beta.12]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.12%2Bmc1.21.11
[0.2.0-beta.11]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.11%2Bmc1.21.11
[0.2.0-beta.10]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.10%2Bmc1.21.11
[0.2.0-beta.9]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.9%2Bmc1.21.11
[0.2.0-beta.8]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.8%2Bmc1.21.11
[0.2.0-beta.7]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.7%2Bmc1.21.11
[0.2.0-beta.6]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.6%2Bmc1.21.11
[0.2.0-beta.5]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.5%2Bmc1.21.11
[0.2.0-beta.4]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.4%2Bmc1.21.11
[0.2.0-beta.3]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.3%2Bmc1.21.11
[0.2.0-beta.2]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.2%2Bmc1.21.11
[0.2.0-beta.1]: https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.1%2Bmc1.21.11
[0.1.1]: https://github.com/glebKovshov/Veyra/releases/tag/v0.1.1%2Bmc1.21.11
