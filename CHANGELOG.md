# Changelog

## 0.2.0 — unreleased development branch

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
- Remaining release blockers include active PBR/depth/shadow sampler reads, pack-specific uniforms, arbitrary geometry vertex inputs, MRT for the other geometry bridges, compute/tessellation execution, and real client/Vulkan pack validation. See COMPATIBILITY.md; this is not a stable full-Iris release.

## 0.1.1

- Fixed include comments, guarded recursion and inactive missing includes.
- Fixed pack refresh/reload, selection persistence and resource cleanup.
- Added 35 regression tests and native shaderc example checks.
- Published a build-verified release with documented GPU validation limits.

- Ensure composite passes execute when the GUI is hidden; retain the pre-GUI path when visible.
