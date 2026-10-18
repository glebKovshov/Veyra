# Changelog

## 0.2.0 — unreleased development branch

- Added a bounded GLSL conditional/macro preprocessor before interface adaptation; inactive declarations no longer confuse adapter validation. Includes remain sandboxed.
- Added shaderpack define/constant options, allowed-value validation, profiles with inheritance and program toggles, sliders, nested screens, pack labels and screen column settings.
- Persisted per-pack overrides atomically without modifying shaderpack files. Failed selection writes restore the previous option file.
- Fixed dimension-qualified `program.world0/name.enabled` conditions and profile program toggles; disabled stages no longer execute merely because their short name lacks a condition.
- Kept discovered packs available when restoring a broken active pack fails.
- Added `world0`, `world-1` and `world1` program overrides and dimension-aware relative includes. Pack/world changes reset temporal matrix and depth history.
- Scheduled prepare, deferred and composite/final at separate rendering boundaries, preserving auxiliary ping-pong buffers between phases. Deferred runs after the opaque depth snapshot and before translucent terrain.
- Initialized first-frame depth history with a real Vulkan transfer clear, including usage/format checks and layout transitions. VulkanMod's unbound `clearDepthTexture` only queues a later attachment clear and is insufficient for immediate sampling.
- Added source/configuration caches scoped to a pack load. Reload creates fresh snapshots; changing options recompiles only when values change.
- Retired unused synthetic native pipelines through VulkanMod's frame cleanup queue and pruned Veyra shader-source caches. Reset lazy shadow and depth-copy pipeline state on reload/disable.
- Deferred sampled depth-view destruction through the frame cleanup queue instead of destroying an image view while submitted descriptors may still reference it.
- Added explicit errors for unsupported required Iris features and active compute, geometry, tessellation or begin stages.
- Added a read-only external shaderpack source audit, 22 new regression tests (57 total), embedded JCPP license notices, distributable checks and CI test-report artifacts.
- Remaining release blockers: arbitrary geometry vertex inputs, geometry MRT, unsupported samplers/custom uniforms, compute/tessellation execution, and real client/Vulkan validation. See COMPATIBILITY.md; this is not a stable full-Iris release.

## 0.1.1

- Fixed include comments, guarded recursion and inactive missing includes.
- Fixed pack refresh/reload, selection persistence and resource cleanup.
- Added 35 regression tests and native shaderc example checks.
- Published a build-verified release with documented GPU validation limits.
