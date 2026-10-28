# Changelog

## 0.2.1 - 2026-10-28

- Analyze entity identifier usage once when preparing pipelines instead of scanning GLSL on every entity submission; skip unused identifier variants and cache shadow-program presence per pack configuration.
- Keep magnified block textures sharp with shader packs while preserving mipmaps and anisotropic minification; reuse Minecraft's sampler for custom block-atlas aliases.
- Restore alpha testing after terrain and entity pack fragments, including early returns, so transparent texels do not write opaque color and depth.
- Move camera bob/hurt transforms from projection into the shader-pack model-view matrices and keep entity vertex and normal transforms consistent with that decomposition.
- Local JDK 21 checks: 617 tests passed, 104 example shaders processed and 122 direct Vulkan SPIR-V modules compiled; the installed BSL v10.1.8 default source audit passed. The user reported improved frame pacing and texture sharpness. Full visual parity and the intermittent black-screen report remain unverified.

## 0.2.0 - 2026-10-27

- Preserve OpenGL shadow texture coordinates under VulkanMod's negative-height viewport, including pack vertex stages and native fallback projection; retain shadow face winding after the Y conversion.
- Compare shadow depth against the shader pack's supplied reference without adding an extra depth bias.
- The matched Complementary HIGH golem fixture now renders the long cast shadow: shadow-region luminance MAE against Iris fell from 22.67 to 5.33. This measurement covers the generated fixture, not complete visual parity.
- Strengthen the entity shadow fixture to require color aliases and near depth in the expected shadow-map quadrant.
- Restore Iris shadow-view rotations and camera snapping; bind the terrain shadow depth image consistently across VulkanMod's descriptor and texture state (#139, #140).
- Permit declared vertex-stage storage-image writes for block-entity shadow programs, restoring the tested Complementary Unbound COMPLEMENTARY lifecycle (#138).
- A matched generated Sildur v2.02 sign fixture on a prerelease candidate measured glyph-mask intersection-over-union 0.9333 after correcting wall occlusion; that sign-specific check was not repeated on the final candidate.
- Correct shader-pack inverse projection inputs, preserve fluid normals and final world/hand depth, and restore post-pass projection coordinates (#142–#145).
- Pass all four selected RTX lifecycle profiles on the final projection candidate. Water color and natural-world parity remain incomplete; see COMPATIBILITY.md.

## 0.2.0-beta.49 - 2026-10-26 (experimental prerelease)

- Attach suppliers to generated geometry UBO fields so loose shader-pack uniforms receive live values (#134).
- Match Iris directional block lighting unless the pack enables `oldLighting` (#135), and convert entity color overlays using Iris semantics (#136).
- Enable declared shadow-entity color outputs (#137). The natural entity cast-shadow discrepancy remained open in beta.49.

## 0.2.0-beta.48 - 2026-10-25 (experimental prerelease)

- Use Iris `shadowProjection` in entity shadow pack mains after legacy source preparation and remap OpenGL clip depth to Vulkan zero-to-one after terrain/entity shadow pack execution (#132).
- Route block-entity text through Iris `BlockTrans`, preserving submission identity, the corresponding pack vertex stage, and native text alpha cutoff (#131). Sildur glyph readability remains unverified because the matched Iris reference fixture is blank too.
- Flush deferred Vulkan image frees before allocator destruction (#130); the new Complementary GPU run exited normally. A historical one-off Windows `0xC0000409` shutdown status has not been reproduced or fully explained.
- On RTX 3070, pinned Complementary Reimagined r5.9.3 HIGH ran at 640x360 with 230,400 non-black pixels / 116 color buckets. Against the matched Iris reference, candidate mean RGB is 106.82/98.85/72.82 vs 121.49/108.53/80.95 (MAE 17.69): materially brighter than beta.47, but still not at Iris brightness.
- Local JDK 21 gate: 595 tests, 0 failures/errors/skips; 104 example shaders, 122 Vulkan SPIR-V compilations, build, graphics-smoke compilation and example-pack packaging passed. PR #132 passed exact-head Build, source audit, and all 30 Graphics jobs.
- The release remains an experimental subset; lifecycle evidence and generated scenes do not certify full packs, natural worlds, or full Iris parity.

## 0.2.0-beta.47 - 2026-10-25 (experimental prerelease)

- Feed loose shader-pack uniforms (`sunAngle`, `worldTime`, `viewWidth`, ...) in sky, particle, weather, entity and shadow-entity RenderPipeline passes; they previously read 0, giving Complementary a pre-dawn sky without clouds (#127).
- Honour Iris `shadowEntities` / `shadowBlockEntities`; Complementary Unbound's COMPLEMENTARY profile no longer fails on a `shadow_block` storage-image write (#128).
- RTX 3070 lifecycle smokes passed: BSL HIGH 360,000 / 124 buckets; Sildur default 360,000 / 122; Unbound HIGH 358,109 / 92; Unbound COMPLEMENTARY 359,042 / 93. In the matched Complementary HIGH fixture, Veyra capture methods are pixel-identical but the output remains darker than Iris. Full visual parity is unverified.

## 0.2.0-beta.46 - 2026-10-25 (experimental prerelease)

- Match Iris shadow near/far projection settings and sun/moon angle while keeping Vulkan shadow drawing and culling in their required depth spaces.
- Bound the mc_Entity declaration check to individual declarations, avoiding runaway regex scans on large shaderpack entity sources.
- JDK 21 gate: 589 tests passed; 104 shader examples and 122 direct Vulkan SPIR-V stages validated.
- Pinned Complementary Reimagined r5.9.3 HIGH lifecycle smoke passed on RTX 3070: 360,000 non-black pixels, 108 color buckets, resize, Nether/Overworld, disable/re-enable and resource reload.
- Shadow color is checked over the full map; exact Iris visual parity and natural-world parity remain unverified.

## 0.2.0-beta.45 - 2026-10-24 (experimental prerelease)

- Bind shader-pack `gaux2` to the geometry color sampler for sign text pipelines and pass its colortex index into Vulkan descriptors.
- Preserve custom texture aliases, including a pack-owned texture named `gaux2`.
- JDK 21 gate: 585 tests passed; 104 shader examples and 122 Vulkan SPIR-V stages validated.
- Source audit accepted 939 geometry fragments across four pinned packs, three dimensions, and 12 defaults / 63 declared-profile cases; 18 active compute stages passed. No visual parity claim.
- RTX 3070 Complementary Reimagined HIGH lifecycle and a dedicated sign-text sampler smoke both passed.

## 0.2.0-beta.44 - 2026-10-24 (experimental prerelease)

- Resolve active, profile-specific geometry DRAWBUFFERS / RENDERTARGETS mappings so Complementary HIGH routes the third fragment output to colortex4.
- Add a regression test for the fallback 06 directive and active 064 override, and ignore directive-shaped text in line comments.
- RTX 3070 pinned Complementary Reimagined HIGH lifecycle smoke passed resize, Nether/Overworld, disable/re-enable and resource reload (359,998 non-black pixels / 109 color buckets).
- Local JDK 21 gate: 584 tests, 0 failures/errors/skips; 104 shader examples, 122 Vulkan SPIR-V stages, shader validation, build, graphics-smoke compilation and example-pack packaging passed. Five Python CI audit tests passed.
- The source-only pinned audit accepted 921 geometry fragments and rejected 18 gbuffers_textured fragments for unsupported gaux2; all 18 active compute stages compiled. No visual parity claim.
## 0.2.0-beta.43 - 2026-10-24 (experimental prerelease)

- Resolve shader-pack post-process colortex mappings to adapted sampler descriptors, including custom textures such as Complementary's cloud-water map.
- Bind pack-owned post-process textures after native render-pass inputs are installed, preventing target setup from overwriting the custom sampler.
- Pinned stock Complementary Reimagined HIGH smoke passed on RTX 3070 with cloud-water bound to VeyraColortex3Sampler and volumetric clouds visible (230,400 non-black pixels / 111 color buckets). A matched Iris HIGH capture used the same archive and fixture; it shows a darker Veyra scene and different sign appearance. This synthetic comparison is not whole-pack parity.
- Local JDK 21 gate: 583 tests, 0 failures/errors/skips; 104 example shaders and 122 direct Vulkan SPIR-V compilations; shader validation, build, graphics-smoke compilation and example-pack packaging passed.

## 0.2.0-beta.42 - 2026-10-24 (experimental prerelease)

- Run the shaderpack-owned `gbuffers_textured.vsh` for sign text and link its color, texcoord and normal outputs to the fragment stage. Lit text reads UV2; see-through text without UV2 uses Minecraft full-bright coordinates.
- Prepare the neutral shadow-sampler fallback before opening the shadow pass, fixing a first-use texture upload inside an active render pass after restoring a saved pack.
- During shadow-map writes, bind the prepared neutral shadow color/depth inputs until the map is complete instead of rejecting those reads.
- RTX 3070 Complementary Reimagined default smoke passed after the shadow-input fix (230,400 non-black pixels / 48 color buckets). This generated-scene check does not certify pack-wide visual parity.
- On NVIDIA RTX 3070, a Sildur lifecycle with a real sign block entity passed resize, Nether/return, disable/re-enable and resource reload. The sign-text pipeline was exercised; glyph readability was not verified.
- Local JDK 21 gate: 582 tests, 0 failures/errors/skips; 104 shader examples and 122 direct Vulkan SPIR-V compilations, shader validation, build and graphics-smoke compilation passed. This is generated-scene evidence, not whole-pack or natural-world certification.

## 0.2.0-beta.41 - 2026-10-24 (experimental prerelease)

- Supply OpenGL's default `mc_Entity` `(0,0,0,1)` to entity/block-entity programs and particle vertex stages, matching Iris' unbound attribute.
- Accept pack-owned `vec4 normal` in particle vertex stages; keep particle `entityColor` at zero; retry fragment-only when a fallback vertex stage fails to compile.
- RTX 3070 lifecycles: Sildur's Vibrant now applies particle and block-entity shaders (sign text still skipped); Complementary, Unbound and BSL unchanged with no skipped overrides. 580 tests; PR #110 passed 57/57 hosted checks.

## 0.2.0-beta.40 - 2026-10-24 (experimental prerelease)

- Run the pack vertex stage for `gbuffers_textured[_lit]` particle fallbacks, retrying fragment-only if it can't be adapted.
- Sign text: supply `northVec`, legacy `color`, a per-pixel face `normal` from `viewPos`, and full-bright `lmcoord` for see-through text without UV2; detect lightmap inputs in comma-separated varyings.
- RTX 3070 lifecycles pass with no skipped particle/sign-text overrides for Complementary Reimagined, Unbound and BSL HIGH; Sildur's Vibrant passes with block-entity `mc_Entity` fallbacks still skipped. 580 tests; PR #108 passed 57/57 hosted checks. Not natural-world visual certification.

## 0.2.0-beta.39 - 2026-10-24 (experimental prerelease)

- Bridge Iris samplers (`gaux2`/`colortexN`, noise, specular, shadow, lightmap) in textured particle fallbacks; previously these Complementary overrides were skipped.
- Supply `upVec`/`sunVec`/`eastVec` to sign-text fragments.
- Local gate: 575 tests, 0 failures. PR #106 passed all 57 hosted checks. Not visually certified on natural worlds.

## 0.2.0-beta.38 - 2026-10-24 (experimental prerelease)

- Add Iris pre-shadow `shadow.csh` and consecutive letter components, with shadow-map-relative sizing, shared image/SSBO bindings, and dispatch barriers. Reject unsupported numbered shadow compute programs explicitly.
- Connect registered VulkanMod suppliers to compute UBO fields; viewport uniforms previously read zero in compute even when fragment uniforms were correct.
- Resolve setup `workGroupsRender` against Iris' 1x1 setup extent. Extend the setup fixture to verify once-only execution, a real 640x360 -> 800x450 resize, and current viewport values without excess invocations.
- The forced versioned local gate passed 573 tests, five Python tooling tests, shader validation, build, packaging and pinned source audit (864 accepted geometry fragments, 18/18 compiled compute stages). RTX fixtures passed pre-shadow ordering at 230,400/0 pixels and setup resize at 360,000/0 pixels. These are synthetic checks, not whole-pack visual certification.
- Pass a fresh beta.38 Complementary Reimagined COMPLEMENTARY lifecycle run: resize, Nether/return, disable/re-enable and reload; 326,462 non-black pixels / 55 color buckets, clean exit, and no shader-pack buffers before allocator destruction. Sign-text upVec and textured-particle gaux2 overrides remain unsupported.
- One Windows shadow-compute run passed readback but exited during native shutdown with 0xC0000409; isolated reruns exited cleanly, and the cause remains unproven. Khronos validation was not enabled on Windows. Hosted gates and versioned release validation remain required before publication.

## 0.2.0-beta.37 - 2026-10-24 (experimental prerelease)

- Add SSBO-only Iris compute programs using `bufferObject.N`, pre-shadow `begin.csh`, `setup.csh`, and pre-shadow `begin.fsh` passes. Preserve setup/begin SSBO contents across later passes and frames; clear frame color targets only once so begin-fragment output survives into the rest of the frame.
- Add focused example packs and hosted Lavapipe graphics-smoke coverage for SSBO-only compute, setup, begin compute, and begin fragment behavior. The versioned local beta.37 candidate's begin-fragment fixture passed on RTX 3070 with 230,400 active / 0 disabled marker pixels. Separate RTX 3070 runs of the SSBO-only, setup, and begin-compute fixtures also reported 230,400 / 0; setup resize was not separately exercised on the physical GPU.
- Pass the local JDK 21 gate with 568 tests (0 failures/errors/skips), 102 example shader sources / 118 direct Vulkan SPIR-V compilations, build, graphics-smoke compilation, and example-pack packaging. The hosted release workflow must pass its fresh source audit and full Lavapipe graphics matrix before it can publish.
- Pass the local pinned source-only audit for four archives / 12 default and 63 declared-profile/dimension cases: 864 geometry fragments accepted / 0 rejected, 18/18 active compute stages compiled. All 75 records retain `visualValidation: false`; this is not whole-pack visual certification.
- Keep writable shadow `voxel_img` conditional on Vulkan `vertexPipelineStoresAndAtomics`; 18 Complementary profile/dimension audit rows report that capability requirement. The tested RTX 3070 enabled it; availability on other devices is not established by the source audit.
- This remains an experimental Iris-compatible subset. These fixtures do not establish full-pack or natural-world visual compatibility; see `COMPATIBILITY.md` for specific evidence and unsupported stages.

## 0.2.0-beta.36 - 2026-10-23 (experimental prerelease)

- Support read-only custom-image sampling from `gbuffers_basic.vsh` and `gbuffers_line.vsh`. Prepare image samplers before native vertex adaptation and merge their descriptor bindings with fragment-stage samplers. Storage-image writes through these basic/line graphics pipelines remain unsupported.
- Extend `examples/basic-custom-image` so both basic vertex and fragment stages consume the compute-backed image; hosted Lavapipe readback measured 471 active green pixels / 0 disabled and no runtime Vulkan validation errors. Add a line vertex example and `LINE` capability regression; the graphics fixture does not draw or certify that line pipeline.
- Final local JDK 21 gate: 554 tests, 0 failures/errors/skips, 102 shader examples, 118 direct Vulkan SPIR-V compilations, build, example-pack packaging, and graphics-smoke compilation. Pinned source audit passed for four archives, 12 defaults and 63 declared-profile cases: 864 geometry fragments accepted / 0 rejected, 18/18 active compute stages compiled. Every audit row has `visualValidation=false`.
- The release workflow gates publication on a fresh pinned source audit, the complete Lavapipe graphics matrix, versioned build/tests, and distributable verification. This remains an experimental Iris-compatible subset; no complete third-party pack or stable Iris parity is claimed.

## 0.2.0-beta.35 - 2026-10-23 (experimental prerelease)

- Support animated resource-pack `_n.png` and `_s.png` maps in block/shadow atlases. Atlas companion maps follow the base sprite's animation frame and sub-frame, including vector-aware normal interpolation and LabPBR/data-channel mip updates; resource-backed entity `SimpleTexture` maps use their own animation clock. Each animated map is capped at four million source pixels, with a 64 MiB source-animation limit per texture material, with separate 128 MiB source-animation LRU caches for normal and specular maps (256 MiB combined). Downloaded player skins are not covered.
- Add the `normal-atlas-animated` GPU fixture. On RTX 3070, the final diagnostic read back 222,724 magenta pixels and 222,722 green pixels across the two synchronized frame states, then 0 of each after shader disable.
- Validate selected `COMPLEMENTARY` lifecycle profiles for Complementary Reimagined and Unbound r5.9.3 on RTX 3070: 337,813 / 57 and 359,954 / 60 non-black pixels/color buckets. Both completed resize, Nether, Overworld return, disable, re-enable, and reload in a synthetic world; this is not full-pack visual certification.
- Pass the fresh pinned source-only audit for 4 archives / 75 profile-dimension records / 5,178 stages: 864 geometry-fragment programs accepted, 0 rejected, and 18/18 active compute stages compiled. Eighteen Complementary profile/dimension records still report the unavailable writable `voxel_img` shadow-vertex capability; audit records do not claim visual validation.

## 0.2.0-beta.34 (experimental prerelease)

- Bind static Iris `_n.png` maps to resource-backed entity `SimpleTexture` albedo images and expose the `normals` sampler in supported entity fragments.
- Keep the entity normal image directly on its Vulkan descriptor so VulkanMod's shared texture-slot cache cannot replace the selected map before descriptor updates.
- Add authored-map and missing-map cow graphics fixtures with active-versus-disabled Vulkan readback. Dynamic player skins, animated material maps, and full Iris parity remain unsupported.

## 0.2.0-beta.31 (experimental prerelease)

- Add custom weather and translucent-particle vertex execution plus comment-safe shader interface scans.
- Add selected `shadowcomp` compute with 3D shader-pack images, shadow color MRT, selected-profile storage buffers, and additional post texture slots.
- Support Iris `workGroupsRender` and validated `indirect.<pass>` dispatch for the supported `shadowcomp` path, including indirect-read synchronization.
- Request supported Veyra Vulkan core features through a separate `pEnabledFeatures` block while preserving VulkanMod's Vulkan 1.1 and dynamic-rendering `pNext` chain.
- Use the selected mip extent for VulkanMod framebuffer rendering, fixing atlas render-area validation errors. Pin a corrected Khronos validation layer and add standalone vertex storage-image probes to the Lavapipe gate.
- Reuse pruned terrain varying locations before high slots, preserve fixed aliases, and reconcile shadow vertex/fragment interfaces; this clears the observed Sildur water interface-component validation error.
- Load shader-pack PNG textures on supported terrain, shadow, and entity geometry through global and stage-scoped mappings; resolve scoped entity samplers using the selected entity pipeline and prepare pack textures before first selection, including after restoring a pack. Honor `.mcmeta` blur/clamp settings.
- Keep shadow-dependent sampling safe across dimension and reload transitions until a completed shadow map is available. Adapt BSL fog uniforms/helpers and preserve shader-pack `texture2DShadow` helpers.
- Pass the combined #85+#86 JDK 21 preflight gate: **523 tests, 0 failures/errors/skips**, 97 example shaders, 113 direct Vulkan SPIR-V compilations, graphics-smoke compilation, build, and example-pack packaging. The pinned audit accepts 864 geometry fragments and compiles 18 active compute stages across 4 archives / 75 cases; every row remains `visualValidation=false`.
- Focused RTX 3070 evidence includes selected Complementary Reimagined/Unbound and BSL HIGH lifecycle runs, `entity-custom-texture` (18,293 active / 0 disabled), and exact #85 `basic-custom-image` (466 active) plus `entity-custom-image` (18,410 active / 0 disabled; requested device feature flags true). These runs do not certify whole-pack compatibility or replace Lavapipe validation. A Sildur `VeyraSmoke` ambient frame assertion passed (24,550 non-black pixels / 69 color buckets), while a later `finalclass` probe failed as nearly uniform (230,400 / 23); full-pack Sildur compatibility is unverified.

## 0.2.0-beta.30 - 2026-10-21 (experimental prerelease)

- Fix the Minecraft `CloudRenderer` crash caused by nested `Tesselator.begin` Mixin remapping.
- Bind the live Minecraft lightmap to supported world-border and sky-textured pipelines, link custom `gbuffers_textured` normals, and remove unused fragment varyings before linking.
- Pass 334 local tests and the pinned audit; full third-party shaderpack visuals remain unverified.

## 0.2.0-beta.29 - 2026-10-21 (experimental prerelease)

- Add supported custom `gbuffers_lightning.vsh`, both native `gbuffers_skytextured.vsh` formats, and the `gbuffers_textured.vsh` world-border path, with checked vertex/fragment varying links and explicit capability validation.
- Specialize Iris `renderStage` for SUN, MOON, and VOID on celestial draws and VOID on end-sky draws. Keep native textured coordinates within Vulkan's vertex-output component limits.
- Allow core `textureSize()` queries for supported 2D samplers and add compiler, unit, example-pack, Lavapipe, and physical-GPU smoke coverage.
- Pass 330 local tests, 85 shader examples, 98 direct SPIR-V compilations, and all 34/34 CI Lavapipe fixtures. The refreshed pinned audit accepts 781 and rejects 8 geometry fragments; full third-party shaderpack visuals remain unverified.

## 0.2.0-beta.28 - 2026-10-21 (experimental prerelease)

- Add Iris `MC_RENDER_STAGE_*` macros and bind `renderStage` to the values available in the sky, stars, and sunrise/sunset pipelines.
- Bind `entityId`, `blockEntityId`, and `currentRenderedItemId` in supported custom entity, block, and hand vertex stages; selected identifier values are included in pipeline cache identity.
- Remove unused `mc_Entity` declarations when shaders do not read them, while keeping explicit capability errors for active reads unavailable in Minecraft's `NEW_ENTITY` format.
- Skip the legacy OptiFine-only `gbuffers_entities_glowing` program, consistent with Iris program discovery.
- Pass 323 local tests, source validation, the pinned 75-case audit, and all 30/30 CI graphics fixtures. Full third-party shaderpack visuals remain unverified.

## 0.2.0-beta.27 - 2026-10-21 (experimental prerelease)

- Add supported custom `gbuffers_damagedblock.vsh` execution with Minecraft's native `BLOCK` vertex format and integer `UV2` lightmap input.
- Adapt VulkanMod's crumbling vertex shader before pipeline compilation to match the native signed integer `UV2` input.
- Add custom `gbuffers_skybasic.vsh` execution for sky, stars, and sunrise/sunset pipelines with checked native formats and linked varyings.
- Add shader compiler, source-audit, example-pack, and Lavapipe graphics coverage. All 30/30 CI graphics fixtures pass.
- The pinned audit accepts 781 and rejects 8 geometry fragments across 4 archives, 75 profiles/dimensions, and 5,178 stages. All audit records have `visualValidation=false`; full third-party pack visuals remain unverified.

## 0.2.0-beta.25 - 2026-10-20 (experimental prerelease)

- Execute a supported subset of `gbuffers_armor_glint.vsh` after Minecraft's glint vertex setup and link checked color, UV, and fog-distance outputs to the adapted fragment stage. Bridge legacy position/UV inputs, texture matrices, Iris fog fields, and `noisetex`; map `gl_Color` to white where Minecraft's `POSITION_TEX` vertex format has no color input.
- Map the glint fragment sampler aliases `texture`, `gtexture`, and `tex` to Minecraft's `Sampler0`. Remove unreachable GLSL helpers and unused uniform/sampler declarations before capability checks; retain explicit errors for active unsupported reads.
- Add adapter regression tests, Vulkan SPIR-V shaderc fixtures, a diagnostic example pack, and an enchanted-book Lavapipe fixture. The fixture read back 313 bright green pixels while enabled and 0 after disabling the pack.
- PR [#62](https://github.com/glebKovshov/Veyra/pull/62) passed Build, source audit, and all 25/25 Lavapipe fixtures before merging to `main` as `df3df827743adf9a978008c59fe9812cd16a699d`.
- The pinned audit accepts 705 and rejects 12 geometry fragments across 4 archives / 75 cases / 5,178 stages. Complementary armor glint passes source adaptation; remaining blockers include `gbuffers_basic.vsh`, Sildur attachment 4 and `LightC`, and selected Complementary compute programs. Every audit record has `visualValidation=false`; no full third-party pack or physical-GPU visual validation is claimed.

## 0.2.0-beta.24 - 2026-10-20 (experimental prerelease)

- Execute supported terrain `gbuffers_terrain`, `_solid`, `_cutout`, and `_water` vertex shaders after VulkanMod terrain setup. Bridge supported legacy terrain inputs, `noisetex`, and checked scalar/vector/matrix vertex-to-fragment varyings. Keep packed tangent layout compatible with VulkanMod 0.6.8 generic float attributes.
- Add regression coverage for terrain vertex execution, varying linking, unsupported inputs, and the scalar tangent attributes. PR #59 passed Build and source audit plus all 24/24 Lavapipe graphics fixtures.
- Publish [Veyra 0.2.0-beta.24+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.24%2Bmc1.21.11) after release-prep PR #60. Release workflow passed source audit, all 24 graphics fixtures, versioned build/tests, and distributable verification. The downloaded install JAR is 642,978 bytes, SHA-256 `edaaa23fe9b4bd5c4a6bfed210b12b407fc983260059c095db5eb32f3bbdd40e`; it matches `SHA256SUMS.txt` and passes `scripts/verify_jar.py`.
- The pinned audit covers 4 shaderpack archives / 75 cases / 5,178 stages, accepting 705 and rejecting 12 geometry fragments. All 75 whole-pack cases still stop at unsupported non-terrain `gbuffers_armor_glint.vsh`; every record has `visualValidation=false`. No full third-party pack visual or physical-GPU validation is claimed.

## 0.2.0-beta.23 - 2026-10-20 (prerelease)

- Reject active non-cloud `gbuffers_*.vsh` stages with a clear capability error when their fragment program exists; Veyra still uses VulkanMod's built-in terrain/entity vertex stages.
- Keep disabled programs and the existing cloud and fullscreen post vertex adapters available. This change does not implement general geometry vertex-stage execution.
- Refresh the pinned audit snapshots. Fragment adapter counts remain 705 accepted / 12 rejected across 75 records and 5,178 stages; all records now show a whole-pack capability error for unsupported active geometry vertex stages, and `visualValidation=false` remains on every row.
- PR #54 passed Build, source audit, and 22/22 Lavapipe graphics fixtures. The release-tag workflow rebuilds and verifies the versioned artifact.

## 0.2.0-beta.22 - 2026-10-20 (prerelease)

- Add auxiliary `colortex1..15` sampling to supported water and entity-family geometry, up to six unique inputs per program.
- When a sampled color target is also a draw attachment, copy its current contents to a separate Vulkan image before drawing; keep the original target writable and resume the render pass correctly.
- Add water and entity feedback examples and Lavapipe coverage. All 22 graphics fixtures pass; the entity feedback readback measured 18,648 active magenta pixels, 0 disabled pixels, and 0 runtime Vulkan validation errors. Vulkan teardown diagnostics remain.
- Refresh the pinned source audit: 705 geometry fragments accepted / 12 rejected across 75 records and 5,178 stages. Remaining Sildur rejections need pack-specific `LightC`; selected Complementary profiles still need the unsupported `shadowcomp.csh` compute stage. No complete third-party pack visuals are validated.

## 0.2.0-beta.21 - 2026-10-20 (prerelease)

- Prune cloud vertex helper functions that are unreachable from shaderpack `main()`, while preserving rejection of unsupported helpers that remain reachable.
- Bridge supported legacy cloud fog fields, `gl_ModelViewMatrix` through VulkanMod's live `ModelViewMat`, lowercase `texcoord`, and cloud `dist`.
- Add cloud model-view shaderc fixtures and a real-client Lavapipe fixture. It read back 52,495 active magenta pixels, 0 disabled pixels, and 0 runtime Vulkan validation errors; all 21 graphics fixtures passed.
- Refresh the pinned source audit: 693 geometry fragments accepted / 24 rejected across 75 records and 5,178 stages; total source errors fell from 84 to 24. Third-party pack visuals remain unverified.

## 0.2.0-beta.20 - 2026-10-20 (prerelease)

- Add active Minecraft cloud-texture sampling for supported `gbuffers_clouds` programs, with reload and Vulkan resource cleanup.
- Bridge cloud UVs, flat face normals, and BSL `sunVec`, `upVec`, and `eastVec` inputs through the supported cloud vertex stage.
- Add cloud texture and directional-input Lavapipe coverage. The fixture read back 46,140 active pixels with 0 runtime Vulkan validation errors; all 20 graphics fixtures passed.
- Refresh the pinned source audit: 690 accepted / 27 rejected geometry fragments across 75 cases and 5,178 stages. BSL defaults accept 9/9 in each tested dimension; these are source and synthetic-fixture results, not full-pack visual validation.

## 0.2.0-beta.19 - 2026-10-20 (prerelease)

- Add terrain `dist` and BSL `recolor` inputs from Veyra's bridged view position and block IDs.
- Add water `sunVec`, `upVec`, and `eastVec`, plus BSL `vTexCoord` and `vTexCoordAM` using the live texture matrix and terrain quad midpoint.
- Refresh the pinned source audit: 672 accepted / 45 rejected geometry fragments across 75 cases and 5,178 stages. The results are source-level only; third-party pack visuals remain unverified.

## 0.2.0-beta.18 - 2026-10-20 (prerelease)

- Bridge legacy Iris `gl_Fog.color`, `gl_Fog.start`, `gl_Fog.end`, and derived `gl_Fog.scale` through Minecraft's existing Fog UBO.
- Reject `gl_Fog.density` explicitly because Veyra has no validated runtime density value; unsupported values are not fabricated.
- Extend the geometry-fog example and adapter regressions. The pinned source audit accepts 636 / rejects 81 geometry fragments across 75 cases / 5,178 stages; no third-party pack visuals are validated.

## 0.2.0-beta.17 - 2026-10-20 (prerelease)

- Bridge Iris terrain `midCoord` from VulkanMod's compressed per-quad midpoint after applying the live texture matrix to midpoint and vertex UVs; derive `signMidCoordPos` and `absMidCoordPos` from those transformed coordinates.
- Initialize VulkanMod's unset mapped texture matrix to identity while preserving valid transforms, preventing terrain and specular shader inputs from collapsing to zero UVs.
- Add direct-buffer uniform regression coverage and extend the terrain midpoint Lavapipe fixture. The pinned source audit accepts 528 / rejects 189 geometry fragments; third-party pack visuals remain unverified.

## 0.2.0-beta.16 - 2026-10-20 (prerelease)

- Bridge Iris terrain `absMidCoordPos` from the existing per-quad midpoint UV without changing the compressed vertex format or stride.
- Add regression, shaderc, and Lavapipe coverage for the absolute midpoint signal.
- Refresh the 75-case pinned source audit: 486 geometry fragments accepted / 231 rejected. No third-party shaderpack is visually validated.

## 0.2.0-beta.15 - 2026-10-20 (prerelease)

- Append Iris `block.properties` IDs and the `mc_Entity` fluid marker to VulkanMod terrain vertices; expose them to terrain shaders as `mat` and `mc_Entity`.
- Rebuild terrain sections after selecting a pack when its block mappings change.
- Handle repeated block-ID entries, comma-separated state property values, and Iris 1.8+ direct block/state priority over tags.
- Add original terrain-ID and water-ID examples and Lavapipe readback fixtures. The fixtures verify the synthetic bridge only; third-party shaderpack rendering is not certified.

## 0.2.0-beta.14 - 2026-10-20 (prerelease)

- Add native multiple color attachments for supported entity, first-person hand, block-entity, and sign-text geometry, retaining explicit and legacy output mappings.
- Preserve entity-specific specular material binding while registering entity-family MRT pipelines.
- Adapt entity output declarations for VulkanMod 0.6.8's GLSL parser and add Lavapipe readback coverage through colortex1.
- Refresh the pinned source audit: 486 geometry fragments accepted and 231 rejected across 75 cases / 5,178 preprocessed stages. No third-party pack is visually validated.

## 0.2.0-beta.13 - 2026-10-20 (prerelease)

- Add Iris `isMainHand` specialization for first-person hand fragments, with separate cached main-hand and offhand pipeline variants.
- Normalize comma-separated legacy varying declarations and bridge BSL `sunVec`, `upVec`, and `eastVec` through Iris celestial/frame inputs.
- Refresh the pinned shaderpack audit: 234 geometry fragments accepted and 483 rejected across 75 cases / 5,178 preprocessed stages; no third-party pack is visually validated.

## 0.2.0-beta.12 - 2026-10-20 (prerelease)

- Map Iris entityColor to Minecraft's overlayColor varying where the selected entity pipeline provides it; keep the transparent fallback on pipelines without that input.
- Normalize the entity-family lmCoord spelling to Veyra's existing lightmap varying.
- Fix a false unsupported-input rejection when entity shaders declare viewPos as a local variable, and constrain line-anchored uniform scans to horizontal whitespace.
- Refresh the pinned shaderpack source audit and document the active source limitations; no third-party pack visual result is claimed.

## 0.2.0-beta.11 — 2026-10-20 (prerelease)

- Bridge Iris `currentRenderedItemId` from the submitted `ItemStack` for entity, block-entity, first-person hand, and shadow render paths. Resolve the active pack's `item.properties` mapping at submission time, including block items.
- Add a real-client graphics smoke fixture with separately mapped diamond and stone item entities and per-ID color readback checks.
- Armor-trim and cape special IDs remain unsupported; this release does not claim full Iris or third-party shaderpack visual compatibility.

## 0.2.0-beta.10 — 2026-10-20 (prerelease)

- Extend VulkanMod's compressed terrain vertices with Iris per-quad midpoint UVs and bridge `signMidCoordPos` using the original vertex UV and that midpoint.
- Bridge terrain `vertexPos`, `playerPos`, and raw `glColorRaw` to source values already carried by the terrain shader.
- Add regression coverage and a Lavapipe fixture that requires both signs of the midpoint signal; add its observed pixel count to the fixture result.
- Update the pinned source audit and compatibility matrix after this bridge.

## 0.2.0-beta.9 — 2026-10-20 (prerelease)

- Bridge terrain-family Iris `viewPos` from VulkanMod's camera-relative vertex position and the current `gbufferModelView` matrix, preserving the configured uniform-buffer layout.
- Add the terrain case to `geometry-view-space`, plus JUnit, direct SPIR-V and Lavapipe readback regressions for the fragment and adapted vertex stages.
- Refresh the pinned source-audit reports: BSL terrain now passes the source adapter in default and declared-profile cases; BSL water still requires `mat`, and Complementary still requires `mc_midTexCoord`.

## 0.2.0-beta.8 — 2026-10-20 (prerelease)

- Add terrain aliases for Iris `lmCoord`/`lightMapCoord` and BSL `color`, using VulkanMod's existing lightmap and vertex-color inputs.
- Name the exact unsupported terrain input in adapter diagnostics; keep the existing legacy `gl_FragData` output rewrite covered by a regression test.
- Refresh the pinned source-audit reports and compatibility matrix: Complementary terrain/water still need `signMidCoordPos`/`mc_midTexCoord`, and BSL terrain still needs `viewPos`.

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
