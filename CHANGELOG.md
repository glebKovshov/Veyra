# Changelog

## 0.2.0-beta.7 — 2026-10-20 (prerelease)

- Add Iris cloud inputs for `colortex0` scene-color sampling and the existing Vulkan-backed `noisetex`, including legacy `texture2DLod` reads lowered to Vulkan GLSL `textureLod`. Veyra snapshots scene color before clouds draw to avoid sampling its active color attachment.
- Remove unused global sampler declarations before cloud adapter validation; an unread `colortex0` declaration in a shared uniforms file no longer blocks cloud loading.
- Add an original `cloud-colortex0` example and a Lavapipe fixture that checks capture, legacy `noisetex` LOD sampling, and the blue-sky magenta signal.
- Keep other cloud samplers, texture coordinates, and sampling functions explicit.

## 0.2.0-beta.6 — 2026-10-19 (prerelease)

- Added Iris' hardcoded `velocity` uniform for terrain, entity-family and post programs; a pack's own `velocity` definition takes precedence, as for `shadowFade`.
- Fixed pack-overridable built-ins after pack switches: selecting a pack that overrides `shadowFade` no longer leaves later packs' geometry programs reading 0.
- Matched Iris custom-uniform semantics: `smooth()` half-lives in tenths of a second, floored `fmod`, and `edge`, `log(base, x)`, `log10`, `exp10`, `random`, `randomInt`.
- Definitions that Iris itself cannot resolve because they reference an unregistered `BIOME_*` constant now stay 0 with a warning, as in Iris, instead of rejecting the program.
- The pinned source audit now seeds vanilla biome ids from Minecraft's `Biomes` class, and terrain uniform rejections name the offending declaration.
- Added `velocity` and a velocity-reading custom uniform to the `custom-uniforms` Lavapipe fixture.
- Added Iris dimension, player, celestial, and hardcoded custom uniforms to supported geometry/post blocks, including pack-overridable hardcoded values and Iris defaults for uniforms Iris does not register.
- Added focused regressions for Iris uniform declarations, custom-uniform inputs, smoothing, and fixed-time Nether/End time behavior.
- The refreshed 75-case source audit remains source-only: 18 geometry fragment programs accepted and 699 rejected; the Complementary terrain/water blockers now identify the missing `lmCoord` input.

## 0.2.0-beta.5 — 2026-10-19 (prerelease)

- Read and adapt `gbuffers_clouds.vsh` when a pack provides it, using VulkanMod's position/color cloud mesh and the supported `color` / `vertexDistance` outputs.
- Translate the cloud vertex `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix`, and `ftransform()` inputs; reject unavailable attributes, texture access, and custom vertex uniforms with a diagnostic.
- Add a runnable cloud vertex example plus include-loading, adapter, and Vulkan SPIR-V regression fixtures.
- Correct the historical compatibility note that still said the beta.4 audit was pending. Cloud `colortex0`, cloud textures, velocity, selected compute stages, and real-pack visual validation remain unresolved.

## 0.2.0-beta.4 — 2026-10-19 (prerelease)

- Read Iris `entity.properties` and `block.properties` with shader-pack option preprocessing, including entity IDs, block state predicates, and registered block tags.
- Routed entity and block-entity submissions to cached Vulkan pipeline variants with mapped `entityId` and `blockEntityId` constants; unmapped identifiers use `-1`.
- Added parser, option-loading, precedence, tag, state-filter, and shader-adapter regression coverage.
- The pinned shaderpack source audit and all 12 Lavapipe graphics fixtures pass for this candidate. Third-party-pack visual validation remains outstanding; see `COMPATIBILITY.md` and `VALIDATION.md`.

## 0.2.0-beta.3 — 2026-10-19 (prerelease)

- Added Iris cloud fog uniforms `fogColor`, `fogStart`, and `fogEnd`, backed by VulkanMod's live cloud uniform buffer and fog uniform providers.
- Added a cloud fog shaderc fixture and expanded the pinned audit; no Complementary or other pack cloud fragment now fails specifically on `fogColor`.
- Updated the source-only compatibility matrix. Complementary cloud shaders still encounter active `colortex0` sampling, and no third-party pack has been visually validated.

## 0.2.0-beta.2 — 2026-10-19 (prerelease)

- Added scalar shader-pack custom-uniform expression evaluation from `shaders.properties`, including supported `uniform.*` and `variable.*` float, int, and bool definitions.
- Added Iris `shadowFade` support using current sun/moon angles, while preserving a pack-defined `shadowFade` expression such as BSL's.
- Added Iris near/far, held item/light, biome precipitation, player mood/look, sky color, atlas size, lightning bolt position, and darkness-light uniforms; added relative eye position uniforms to post programs.
- Fixed `ivec2` post-uniform blocks and the handling of Iris-unbound entity identifiers and `entityColor`.
- Pinned source audit covers 75 cases and 5,178 preprocessed sources; 18 geometry fragment cases are accepted and 699 rejected. This remains a source-only result.
- See [RELEASE_NOTES.md](RELEASE_NOTES.md) for installation, verification and known limits.

## 0.2.0-beta.1 — 2026-10-19 (prerelease)

- Added post-stage shadowtex1 and shadowcolor0/shadowcolor inputs, with explicit metadata, color alias deduplication, depth/color filtering and a combined twelve-sampler budget.
- Preserved modern float and legacy vec4 comparison-helper semantics in post shaders; added a four-phase prepare/deferred/composite/final readback chain.
- Fixed swapped usage/native texture ID arguments in borrowed depth views. Standard post binding could otherwise leave a stale light texture selected; alias cleanup also used the wrong numeric ID.
- Bounded post/vertex declaration scans across long blank regions; the new runtime fixture includes 60,000 blank lines per post stage and an unused noisetex header.
- Added a separate pre-translucent `shadowtex1` image for terrain/entity programs, copied on the graphics queue after opaque terrain and entity/block features. Translucent shadow terrain now resumes the existing target with LOAD/STORE.
- Fixed final shadow depth layout when entity feature passes leave it as an attachment, restored main matrices after shadow translucency, and retire cached pipeline variants for released shadow passes at frame completion.
- Added water/entity Vulkan fixtures that require different authored depths from shadowtex0 and shadowtex1; missing/aliased/late snapshots cannot pass.
- Added completed shadow color sampling (`shadowcolor0` / legacy `shadowcolor`) to terrain and entity-family bridges. Shadow attachment feedback and native-slot collisions fail explicitly.
- Added separate terrain/entity shadow-color Vulkan readback fixtures using authored shadow shader output.
- Added resource-pack static specular material textures to terrain/shadow/entity bridges, including atlas padding, straight UNORM channels and linear/LabPBR mipmaps. Missing maps use the zero default; animated maps are explicitly unsupported.
- Added original atlas-mipmap and entity-material Vulkan fixtures. Runtime validation is tracked in VALIDATION.md.
- Added auxiliary color sampling to water geometry, including legacy aliases, post-deferred buffer sides, read-only target initialization and graphics-ordered sampling barriers. Same-attachment feedback is rejected.
- Added a water-color-inputs Vulkan readback case alongside the existing MRT and water-depth fixtures.
- Aligned active noisetex detection between runtime descriptors and the GLSL bridge; unused header declarations no longer shift later bindings.
- Bounded sampler/uniform declaration scans across long preprocessor blank regions, with a 60,000-line regression.

- Added copied opaque depth sampling to `gbuffers_water`, with shared post/terrain snapshot ownership and a dedicated software Vulkan readback fixture.
- Fixed optional terrain descriptor binding gaps when shadow/noise samplers are absent, including noise-only terrain layouts.
- Fixed borrowed main-depth native table binding in the depth-copy pass; the water fixture exposed sampling of its own color attachment and Vulkan layout errors.

- Bound total include-expansion work across recursive sibling branches before preprocessing, with a regression test.
- Made the full pinned source/profile audit runnable on Windows using a JSON argument file and the Windows Gradle wrapper.
- Required pinned source audit and synthetic Vulkan graphics smoke before release publication, and enabled both checks on relevant pull requests. These gates do not establish popular-pack visual compatibility or clean device teardown.
- Added a bounded GLSL conditional/macro preprocessor before interface adaptation; inactive declarations no longer confuse adapter validation. Includes remain sandboxed.
- Added shaderpack define/constant options, allowed-value validation, profiles with inheritance and program toggles, sliders, nested screens, pack labels and screen column settings.
- Persisted per-pack overrides atomically without modifying shaderpack files. Failed selection writes restore the previous option file.
- Fixed dimension-qualified `program.world0/name.enabled` conditions and profile program toggles; disabled stages no longer execute merely because their short name lacks a condition.
- Kept discovered packs available when restoring a broken active pack fails.
- Added `world0`, `world-1` and `world1` program overrides and dimension-aware relative includes. Pack/world changes reset temporal matrix and depth history.
- Added Iris' integer `moonPhase` uniform to the shared geometry and post-pass frame snapshot, using the active dimension's moon phase.
- Added Iris' `bedrockLevel`, `cloudHeight` and 1.21.9+ `endFlashIntensity` uniforms to terrain and post-processing programs, sourced from the active level and its environment attributes.
- Added the Iris entity `varying vec4 color` input alias, bridged to VulkanMod's entity vertex color and covered by a regression test.
- Added an Iris-compatible generated `noisetex`, including `noiseTextureResolution` and linear/repeat sampler binding for supported geometry, shadow, and post programs.
- Exposed Iris' raw `eyeBrightness` and smoothed `eyeBrightnessSmooth` `ivec2` uniforms to geometry and post programs. The time-based smoothing half-life reads `const float eyeBrightnessHalflife` from GLSL and defaults to ten game ticks.
- Removed unused shaderpack sampler declarations from geometry stages before interface validation, while still rejecting active reads from unsupported samplers.
- Lowered `sampler2DShadow` helper parameters in terrain shaders to Veyra's raw-depth sampler type, so the Vulkan software depth comparison also works through common Iris/OptiFine helper functions.
- Bound Iris `shadowtex0` to the actual Vulkan shadow depth view for entity, block entity, hand, and sign-text fragment programs when a shaderpack supplies a shadow pass. This uses VulkanMod's `Sampler3` descriptor slot; a second shadow map is still unsupported.
- Defaulted `endFlashIntensity` to zero in synthetic or transitional client levels where Minecraft has no active End flash state.
- Bridged the legacy `tex` sampler to VulkanMod's atlas and allowed standard `textureSize` queries for bridged samplers; added a SPIR-V example and adapter regressions.
- Scheduled prepare, deferred and composite/final at separate rendering boundaries, preserving auxiliary ping-pong buffers between phases. Deferred runs after the opaque depth snapshot and before translucent terrain.
- Initialized first-frame depth history with a real Vulkan transfer clear, including usage/format checks and layout transitions. VulkanMod's unbound `clearDepthTexture` only queues a later attachment clear and is insufficient for immediate sampling.
- Added source/configuration caches scoped to a pack load. Reload creates fresh snapshots; changing options recompiles only when values change.
- Retired unused synthetic native pipelines through VulkanMod's frame cleanup queue and pruned Veyra shader-source caches. Reset lazy shadow and depth-copy pipeline state on reload/disable.
- Deferred sampled depth-view destruction through the frame cleanup queue instead of destroying an image view while submitted descriptors may still reference it.
- Added native terrain MRT, output-location/colortex mapping, per-program and per-buffer blend properties, device capability checks, and a two-output fixture. Unbound GLSL outputs retain private dataflow without allocating nonexistent attachments.
- Preserved viewport/scissor across geometry target switches and retired cached pipeline variants for removed native passes at completed-frame fences.
- Corrected swapchain image usage metadata and requested transfer-source usage only where the surface supports it. Ordered sampled color-image transitions in the main frame graph after their producers.
- Added a development-only real Minecraft world/readback harness using software Vulkan in CI. Runtime results remain separate from unit/build success.
- Added explicit errors for unsupported required Iris features and active compute, geometry, tessellation or begin stages.
- Added a checksum-pinned external shaderpack source-audit workflow for four public packs and their declared profiles, embedded JCPP license notices, distributable checks and CI test-report artifacts.
- Updated GitHub Actions to Node 24 action releases, pinned hosted runners to Ubuntu 24.04, and selected Gradle's open-source basic cache provider.
- Remaining release blockers include normal/animated PBR maps, wider sampler/property semantics, per-draw IDs and other pack uniforms, arbitrary geometry vertex inputs, MRT for the other geometry bridges, compute/tessellation execution, cleanup/stress and real-pack Vulkan validation. See COMPATIBILITY.md; this is not a stable full-Iris release.

## 0.1.1

- Fixed include comments, guarded recursion and inactive missing includes.
- Fixed pack refresh/reload, selection persistence and resource cleanup.
- Added 35 regression tests and native shaderc example checks.
- Published a build-verified release with documented GPU validation limits.

- Ensure composite passes execute when the GUI is hidden; retain the pre-GUI path when visible.
