# Veyra 0.2.0-beta.42+mc1.21.11 - experimental prerelease

Beta.42 runs shaderpack-owned vertex code for sign text and fixes lazy shadow fallback preparation before the shadow render pass.

## Changes

- **Sign text vertex stage:** adapt and execute `gbuffers_textured.vsh` when present, then link its color, texcoord and normal outputs into the sign fragment stage. Lit text uses the native UV2 input; see-through text without UV2 receives Minecraft full-bright coordinates.
- **Shadow pass safety:** prepare neutral fallbacks before opening the shadow render pass, and bind neutral shadow color/depth inputs while the shadow map is incomplete instead of rejecting those reads.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.42+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance; do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack, then Configure / Apply to edit supported options and profiles.

## Validation and known limits

The local JDK 21 gate passed 582 tests without failures, errors or skips; it validated 104 example shaders and compiled 122 direct Vulkan SPIR-V stages, and passed shader validation, build and graphics-smoke compilation. The pinned Sildur source audit found 42 stages and no preprocessing/adaptation errors. The RTX 3070 Sildur generated-scene lifecycle passed resize, Nether/return, disable/re-enable and resource reload with a real sign block entity. This exercises the sign-text pipeline but does not verify glyph readability.

The beta.42 Complementary Reimagined default smoke passed after the shadow color/depth fallback fix (230,400 non-black pixels / 48 color buckets). The Sildur lifecycle also passed on beta.42; Complementary Unbound and BSL lifecycle results are from beta.41. The controlled 640x360 generated-scene capture pair uses the same camera, day time and block layout; Veyra is substantially darker, has no clouds and renders a visibly different wall than Iris 1.10.8 + Sodium 0.8.14. This synthetic-scene parity defect is unresolved. No natural-world parity or full-pack certification is claimed. The historical Windows shutdown exit `0xC0000409` remains unexplained. A local VulkanMod profile also crashed in Reflex 1.1.2's direct OpenGL query call before Veyra loaded; see `COMPATIBILITY.md` for scope.

Veyra remains an experimental Iris-compatible subset, not a stable Iris-parity release. See `COMPATIBILITY.md` and `ARCHITECTURE.md` for measured scope and unsupported stages.
