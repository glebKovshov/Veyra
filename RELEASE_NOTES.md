# Veyra 0.2.0-beta.49+mc1.21.11 - experimental prerelease

Beta.49 includes the beta.48 shadow-depth and sign-text routing fixes, then adds Iris-style loose-uniform suppliers, directional block shading and entity color overlays. It also enables declared color outputs for shadow-entity pipelines. This does not prove that entity shadow depth is written correctly; the single forced-depth probe was inconclusive. Full Iris visual parity and stable compatibility are not claimed.

## Changes

- **Entity shadow color:** Enable declared fragment color outputs for shadow-entity pipelines and add a regression fixture with a constant entity output to isolate color writes from custom varying linkage (#137).
- **Entity overlays:** Convert vanilla entity and hurt overlay colors using Iris' `entityColor` rules (#136).
- **Directional shading:** Match Iris face shading unless the shader pack enables `oldLighting` (#135).
- **Loose uniforms:** Attach VulkanMod value suppliers to generated UBO 0 fields across geometry pipelines (#134).
- **Shadow depth and sign text:** Preserve Vulkan shadow depth remapping and Iris `BlockTrans` sign-text routing, including the pack vertex stage and native alpha cutoff (#131, #132).
- **Other fixes:** Honor shadow-entity toggles and drain deferred Vulkan image frees before allocator destruction (#128, #130).

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.49+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack, then use Configure / Apply for supported options and profiles.

## Validation and known limits

- The beta.49 candidate containing PR #137 head `68a89ad` passed the local JDK 21 gate: 597 tests (0 failures/errors/skips), 104 example shaders, 122 Vulkan SPIR-V compilations, GraphicsSmoke compilation, build, shader validation and example-pack packaging. `scripts/verify_jar.py` accepted the 1,068,866-byte mod JAR (SHA-256 `9013C00509B3FE6B3D359F3BFA80B02833F50B3F5A856AF159C05F15EF9764D9`).
- The pinned source audit accepted 939 geometry fragments with none rejected and compiled all 18 selected compute stages across four pinned packs, three dimensions, 12 defaults and 63 declared-profile cases. All 75 records are source-only; 18 Complementary Reimagined/Unbound profile-dimension cases need `vertexPipelineStoresAndAtomics` for `voxel_img` shadow-vertex writes, unavailable on the audit device.
- The targeted RTX 3070 shadow-color fixture passed with 217 active marker samples / 0 after disabling the pack. No Vulkan validation layer was enabled. Earlier third-party pack captures and lifecycle runs in `COMPATIBILITY.md` were made on beta.47/beta.48 code and are not fresh full-pack validation of beta.49.
- Entity shadow depth remains unresolved. The forced near-depth readback found no near-zero samples in its probe area, but the golem footprint was at the UV edge and the player's shadow submission was not confirmed. No second GPU run was made.
- Sildur sign glyph readability is unverified; the matched Iris fixture was blank too. Complementary Reimagined HIGH remained darker than Iris in the beta.48 capture.
- These generated-scene and selected-profile runs do not certify natural worlds, complete packs, or all options. A previous Windows shutdown once ended with status `0xC0000409`; a later run exited normally, but the old status is unexplained.

Veyra remains an experimental Iris-compatible subset. Full Iris visual parity and stable compatibility are not claimed.
