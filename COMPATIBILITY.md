# Shaderpack compatibility: Veyra 0.2.0-beta.16 prerelease

## Unreleased terrain-midpoint source candidate (post-beta.16)

The local audit of the current `feature/terrain-midcoord-input` source, using the same four SHA-512-pinned archives, covered 75 default/profile/dimension cases and all 5,178 preprocessed stages. It accepted **528** geometry fragment programs and rejected **189**; every record still has `visualValidation: false`. These numbers describe this unmerged source candidate, not the published beta.16 JAR.

The candidate adds the flat `midCoord` terrain input and applies VulkanMod's current texture matrix to the midpoint and terrain UV before deriving `signMidCoordPos` and `absMidCoordPos`. It initializes VulkanMod's mapped texture matrix to identity if its contents are unset, while preserving valid live transforms. Complementary Reimagined and Unbound r5.9.3 now accept **8/10** default geometry fragments in Overworld, Nether, and End, up from 7/10. Their remaining default blockers are unsupported translucent block/entity inputs; selected profiles still require the unimplemented `shadowcomp.csh` compute stage. BSL remains at 6/9 and Sildur's Vibrant at 0/5. No third-party pack has been visually validated. The initial PR graphics run exposed three synthetic fixture failures from the unset matrix. After the initialization fix, the corrected head passed all 19 Lavapipe fixtures, including `geometry-mid-texcoord`; runtime Vulkan validation errors for that fixture were zero.

## Pinned source audit (beta.16 prerelease)

The release audit [#39387855512](https://github.com/glebKovshov/Veyra/actions/runs/39387855512), artifact [12248416739](https://github.com/glebKovshov/Veyra/actions/runs/39387855512/artifacts/12248416739), covers 75 default/profile/dimension cases and all 5,178 preprocessed stages. It accepts **486** geometry fragment programs and rejects **231**; every case has `visualValidation: false`. This is a source compatibility check, not proof of a complete shaderpack pipeline or rendered output.

| Pack | Default geometry fragments accepted / attempted (OW, Nether, End) | Remaining default blockers | In-game pack result |
| --- | --- | --- | --- |
| Complementary Reimagined / Unbound r5.9.3 | 7/10, 7/10, 7/10 | terrain: `midCoord`; translucent block/entities: unsupported inputs; selected profiles: `shadowcomp.csh` compute stage | Not tested |
| BSL 10.1.8 | 6/9, 6/9, 6/9 | terrain: `recolor`; water: `dist`; clouds: active `texture` sampler | Not tested |
| Sildur's Vibrant 2.02 Extreme | 0/5, 0/5, 0/5 | `colortex4` in translucent geometry; water samples an attachment; clouds require unsupported inputs | Not tested |

The absolute-midpoint terrain bridge added for beta.16 resolves the former `absMidCoordPos` adapter blocker in the pinned Complementary sources and raises their default geometry acceptance from 6/10 to 7/10 in each tested dimension. The remaining `midCoord` identifier is a separate unsupported input reported by the audit. No third-party pack has been visually validated.

## Terrain tangent-space and material inputs (beta.15 prerelease)

The beta.14 source audit rejected Complementary water at `viewVector`. Beta.15 reconstructs a view-space terrain tangent frame from fragment derivatives of view position and UV, then adapts Iris `normal`, `tangent`, `binormal`, and `viewVector`. Comma-separated and interpolation-qualified varying declarations are normalized before validation. The frame is initialized in a wrapper before the shader-pack fragment `main`, keeping derivative evaluation outside shader-pack control flow. This reconstruction targets planar terrain faces; custom geometry with authored per-vertex tangents is not covered.

Beta.15 appends Iris `block.properties` IDs and the `mc_Entity` render-type marker to VulkanMod's compressed terrain vertices. The terrain adapter accepts `mat` as `int` or `float`, plus `mc_Entity` as `vec2`; the fluid marker is `1.0` and the non-fluid marker is `-1.0`. Mapping changes rebuild terrain sections after a pack is selected. The parser handles repeated block-ID lines, comma-separated property values, block tags, and Iris 1.8+ direct-block-over-tag priority.

Release workflow [39382531868](https://github.com/glebKovshov/Veyra/actions/runs/39382531868) passed source audit, all 19 Lavapipe fixtures, final build, JAR verification, and publication on tag commit `b3b6d0e0d46efb62a22e3769509f785e6961481e`. Tag Build [39382531862](https://github.com/glebKovshov/Veyra/actions/runs/39382531862) passed 226 tests with 0 failures, 0 errors, and 0 skipped. Terrain-ID readback was 223,462 active / 0 disabled magenta pixels; water-ID was 226,902 / 0 disabled. Both reported 0 runtime validation errors before shutdown, with 10,031 and 10,050 teardown diagnostics respectively. Source audit artifact [12246036591](https://github.com/glebKovshov/Veyra/actions/runs/39382531868/artifacts/12246036591) contains 75 cases, 4 pinned packs, and 5,178 stages: 432 geometry fragments accepted / 285 rejected, with `visualValidation=false` in every record. These synthetic/source-level results do not visually validate a third-party pack or physical GPU.

## Entity-family multiple render targets (beta.14 prerelease)

Entity, first-person hand, block-entity, and sign-text fragments can route supported explicit and legacy outputs to native color attachments. This extends the existing Vulkan MRT path to these geometry families; it does not add general MRT support to every geometry bridge or certify a complete third-party pack.

The Lavapipe `entity-mrt` fixture sends a magenta signal through `colortex1` into the final pass. On checked head `38a4089504cdedbc2be9ba0964aa6991394cd5cb`, graphics run [39373977059](https://github.com/glebKovshov/Veyra/actions/runs/39373977059) passed all 17 fixtures: entity MRT read back 18,532 active magenta pixels and the entity specular fixture read back 18,293. Both reported zero runtime Vulkan validation errors. Process teardown still emitted 10,035 validation diagnostics; the graphics shutdown is not clean. These are synthetic Lavapipe checks, not third-party pack visuals or physical-GPU results.

## Entity overlay, lightmap aliases, and adapter diagnostics (beta.12)

Iris defines `entityColor` as the current entity overlay and documents its compatibility mapping to the `vaUV1` overlay attribute ([uniform reference](https://github.com/IrisShaders/docs/blob/main/src/content/docs/current/Reference/Uniforms/rendering.mdx#entitycolor), [compatibility mapping](https://github.com/IrisShaders/docs/blob/main/src/content/docs/current/How%20To/compatibility_vs_core.mdx)). Minecraft 1.21.11's standard entity vertex shader samples that overlay into `overlayColor`; Veyra now maps the Iris uniform to this varying. The item-entity pipeline that does not emit an overlay varying and sign/text programs keep the transparent zero fallback.

The entity bridge also normalizes Iris's `lmCoord` spelling to the existing `lmcoord` lightmap input. The fresh source audit no longer reports `entityColor` or `lmCoord` as blockers. It also no longer mistakes Complementary's locally declared `vec3 viewPos` variables for missing fragment inputs. Supported entity-family outputs now use the native MRT path; translucent block/entity programs still reject unsupported inputs, and Complementary water still reaches `viewVector`. BSL hand/entity/block programs pass source adaptation; its remaining default blockers are water `mat` and the active cloud `texture` sampler. These are source-adapter checks only; no third-party pack has been visually validated.

Beta.8 added terrain aliases `lmCoord` and `color`; beta.9 added terrain `viewPos`. Beta.10 extends VulkanMod's 16-byte compressed terrain vertex to 20 bytes, appending two unsigned 16-bit midpoint UV components per quad. The terrain writer computes the average UV from each group of four vertices, while existing position, lightmap, UV0 and color bytes retain their offsets. Iris defines `mc_midTexCoord` as the center UV of the current quad, calculated per quad ([Iris attribute reference](https://shaders.properties/current/reference/attributes/mc_midtexcoord/)). The adapted vertex shader computes `signMidCoordPos = sign(texCoord0 - mc_midTexCoord)`. Terrain aliases `vertexPos` and `playerPos` use the existing camera-relative terrain position; `glColorRaw` uses the raw unpacked terrain color before lightmap multiplication.

Unit tests, direct shaderc compilation, the source audit, and the dedicated `geometry-mid-texcoord` Lavapipe fixture cover these conversions. The fixture passed as part of [graphics run 39348939401](https://github.com/glebKovshov/Veyra/actions/runs/39348939401), including its active and disabled signal checks. No third-party shaderpack has been visually checked. Water's Complementary `viewVector` is intentionally still unsupported because VulkanMod does not provide the pack's tangent basis, so the adapter does not substitute a different vector. Other registered and unsupported inputs remain explicit in diagnostics.

## Current rendered item IDs (beta.11 candidate)

Iris exposes `currentRenderedItemId` for the item or armor currently being rendered, including in gbuffers and shadow programs ([item mapping reference](https://shaders.properties/current/reference/miscellaneous/item_properties/), [ID uniform reference](https://shaders.properties/current/reference/uniforms/id/)). Veyra retains the source `ItemStack` on Minecraft's item render state and resolves it when nodes are submitted, so changing packs uses the active pack's mappings. All item stacks, including block items, use `item.properties`; `block.properties` remains for block and block-entity IDs. Mapped IDs select cached per-item pipeline variants for entity, block-entity, first-person hand, and entity-shadow renders. Other paths receive `-1`.

Iris' hardcoded armor-trim and cape identifiers are not mapped yet. The Lavapipe `examples/current-rendered-item-id` fixture equips mapped diamond (ID 42) in the main hand and mapped stone (ID 43) in the offhand; its fragment requires both the item ID and the correct hand signal. The fixture passed in [graphics run 39366976302](https://github.com/glebKovshov/Veyra/actions/runs/39366976302), with 32,321 active magenta pixels for the main-hand item, 14,102 active green pixels for the offhand item, and zero pixels for both signals when shaders are disabled. This validates the synthetic submission path only. The pinned source audit no longer reports `currentRenderedItemId` as a blocker, but acceptance is still limited by other inputs such as water `viewVector`/`mat`, compute passes, and attachment feedback. All audit records keep `visualValidation=false`; the listed third-party packs remain **Not tested** in-game.

## Iris `isMainHand` and BSL direction inputs for first-person hands (PRs #21 and #22)

First-person hand fragments may declare Iris `varying float isMainHand`. Veyra scopes the logical Minecraft hand around each item submission and specializes cached Vulkan pipeline variants to `1.0` for the main hand and `0.0` for the offhand. The input is accepted only by the hand-program adapter. The `current-rendered-item-id` Lavapipe fixture checks `isMainHand` together with IDs 42 and 43, so either signal alone cannot satisfy the readback.

The BSL fragment declares legacy varyings in comma-separated lists. Veyra now splits those declarations before input validation, maps `sunVec` to the live Iris `sunPosition` vector, and derives `upVec` and `eastVec` from the current `gbufferModelView` axes. A BSL-style hand shader is covered by the shaderc SPIR-V fixture. The current pinned audit accepts the default BSL hand/entity/block fragment cases and 6/9 geometry fragments per tested dimension; the remaining terrain `recolor`, water `dist`, and cloud `texture` blockers are listed in the current matrix above. BSL is not confirmed playable. All 75 current audit records have `visualValidation=false`; no third-party pack has been visually validated.

## Entity and block entity identifiers (beta.4)

Veyra reads option-preprocessed Iris `entity.properties` and `block.properties`. Entity type mappings feed `entityId`; block IDs, partial state predicates and registered block tags feed `blockEntityId`. Block state rules follow source order, so the first matching selector wins. The renderer captures the identifier for each entity or block entity submission and routes its `RenderType` to a cached Vulkan pipeline variant with compile-time identifier constants. Unmapped values are `-1`.

Unit tests cover parsing, namespace defaults, duplicate precedence, conditional properties, tags, block-state predicates and shader constants. This does not establish that a third-party pack renders correctly. Iris' built-in legacy block ID table is not loaded when `block.properties` is absent; such states remain unmapped. The beta.4 source audit and all 12 synthetic Lavapipe graphics fixtures pass, but no real-pack visual validation is claimed.

## Cloud scene-color sampler (beta.7 candidate)

Cloud fragments can directly read the completed scene color through `colortex0` and the `gcolor`, `Sampler0`, or `InSampler` aliases using `texture`, `texture2D`, `textureLod`, or legacy `texture2DLod`; `textureSize` is also available. Veyra copies the main color image into a separate sampled image before clouds draw, restores the scene layout, then binds the snapshot to the cloud pipeline. This avoids reading the color attachment while clouds are writing it. A resize or format change recreates the snapshot. The existing Vulkan-backed `noisetex` is also available in the cloud fragment stage, including LOD reads. Unused global sampler declarations are stripped before validation, so declarations that are not read no longer reject the cloud pass.

The original `examples/cloud-colortex0` fixture samples a known sky color and also makes an active LOD read from `noisetex`; it changes cloud output only when the copied color is blue and the normalized noise read is valid. The Lavapipe fixture checks capture and rendered signal. This validates the Veyra bridge with a synthetic shader, not a complete third-party pack. Other cloud samplers, texture coordinates, and sampling functions beyond `texture`, `texture2D`, `textureLod`, `texture2DLod`, and `textureSize` remain unsupported; capture also requires a transferable main color image.

## Cloud fog uniforms

Cloud fragments now map Iris `fogColor`, `fogStart`, and `fogEnd` to VulkanMod 0.6.8's live `FogColor`, `FogEnvironmentalStart`, and `FogEnvironmentalEnd` uniform providers. Veyra extends both stages' binding-1 uniform block and pipeline configuration for only the fields a pack declares. Unit and shaderc fixtures cover the GLSL mapping; the pinned 75-case source audit has no remaining cloud `fogColor` adapter rejection. This does not add cloud texture coordinates, extra outputs, or full-pack visual validation.

## Cloud vertex shader (beta.5 candidate)

When a pack provides `gbuffers_clouds.vsh`, Veyra now adapts it against VulkanMod's `POSITION_COLOR` cloud mesh. The bridge supports `Position`, `Color`, `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix`, `ftransform()`, and the `color` / `vertexDistance` varyings. It initializes the normal VulkanMod color and distance outputs before running the pack's vertex main, so a shader can override only the values it needs. Texture coordinates, normals, texture sampling, other vertex inputs/outputs, and custom vertex uniforms are rejected with a diagnostic. Fragment-stage cloud uniform support is unchanged. Unit, include-loading, and shaderc SPIR-V fixtures cover this subset; no third-party cloud pack has been visually checked.

## Static material inputs (development)

The geometry bridges now read resource-pack `_s.png` specular maps for terrain/shadow atlases and standalone entity textures. Atlas rectangles follow the live albedo sprites, including edge padding. Data use straight UNORM channels, with linear or LabPBR category-aware mipmaps selected by `optifine/texture.properties`. Missing maps use the zero specular default. Animated material maps and normal maps remain unsupported; animated specular inputs produce an explicit error. General Iris filtering/mipmap settings are incomplete.

The current pinned source audit preprocesses 5,178 sources across 75 default/profile/dimension cases; geometry adapters accept 72 and reject 645 cases. The additional accepted cases come from Complementary's cloud scene-color and noise sampling subset. `shadowFade` is no longer a BSL blocker. Cloud `fogColor` is mapped, but other blockers remain. The matrix is source-only and does not establish popular-pack visual compatibility. Original atlas/entity fixtures validate material data separately.

## Completed shadow color inputs

Terrain and entity-family fragments read the existing completed shadow color attachment through `shadowcolor0` and the legacy `shadowcolor` alias. Both aliases share one descriptor. Sampling is linear/clamp at base level; general shadow color format/mipmap properties, additional attachments are incomplete. Terrain reserves native slot 11, leaving at most five distinct auxiliary water inputs when shadow color is active. Missing shadow passes, shadow attachment feedback and slot collisions fail explicitly. Original terrain/entity GPU fixtures read authored shadow output with zero runtime validation errors; see VALIDATION.md. The separate depth map described below is also available; neither path verifies a complete third-party pack.

## Separate pre-translucent shadow depth

Terrain and entity-family fragments can read `shadowtex1` as sampler2D or sampler2DShadow with the software comparison bridge. It is a separate depth image copied after opaque terrain and entity/block features, before translucent terrain. Sampling is nearest/clamp at base level. Shadow attachment feedback fails explicitly. Terrain reserves native slot 10; water auxiliary assignment skips occupied slots 10/11, allowing five inputs with shadowtex1 alone or four with both shadowtex1 and shadow color. GPU fixtures require different authored values at the same coordinate in shadowtex0/shadowtex1, so aliases and late snapshots cannot pass. Post programs also read these images as described below; the context-dependent legacy shadow alias and general sampler/mipmap properties remain incomplete.

## Post shadow inputs

Prepare, deferred, composite and final programs read actual shadowtex0/shadowtex1 and shadowcolor0/shadowcolor views. Color aliases share one descriptor. Unused declarations do not reserve resources; invalid sampler types, duplicates, missing shadow passes, unsupported shadow names and more than twelve total descriptors produce errors before drawing. The original four-phase GPU fixture passes sentinel data through colortex4/5/6 and requires distinct depth values plus authored shadow color at every phase. It covers modern float and legacy vec4 comparison helpers, an unused noisetex header and 60,000 blank lines per post source. Wider texture operations, mip/filter properties and the context-dependent shadow alias remain incomplete. These synthetic checks do not establish third-party-pack visual support.

## Beta.3 source checkpoint (historical)

This is the beta.3 **source-only compatibility audit** and is retained as a historical baseline. It does not compile complete shaderpack pipelines or render third-party packs. The refreshed pinned archives cover 12 default configurations and 63 declared-profile/dimension configurations. All 5,178 selected stage sources were discovered and preprocessed. The adapters accepted 18 and rejected 699 geometry fragment/configuration combinations. None of the listed packs has a complete pipeline confirmed in-game. The beta.4 audit for the same pinned inputs is recorded below. Neither this historical source audit nor synthetic Lavapipe fixtures are third-party-pack visual validation.

| Pack | Version | Options / profiles | Default stages preprocessed, Overworld / Nether / End | Default geometry fragments accepted / attempted | Blockers at beta.3 | In-game pack result |
| --- | --- | --- | --- | --- | --- | --- |
| Complementary Reimagined | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Custom `blockEntityId`, terrain/water `velocity`, active cloud `colortex0`; `shadowcomp.csh` compute in selected profiles. Cloud `fogColor` is mapped. | Not tested |
| Complementary Unbound | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Custom `blockEntityId`, terrain/water `velocity`, active cloud `colortex0`; `shadowcomp.csh` compute in selected profiles. Cloud `fogColor` is mapped. | Not tested |
| BSL | 10.1.8 | 383 / 5 | 63/63, 59/59, 63/63 | 1 / 9 | Custom `entityId` / `blockEntityId`; cloud `texture` uniform; source-only evaluator lacks registered `BIOME_DESERT` for `isDesert` | Not tested |
| Sildur's Vibrant | 2.02 Extreme | 115 / 0 | 42/42, 42/42, 42/42 | 0 / 5 | `colortex4` in translucent entity/block/hand geometry; water reads colortex4 while it is also an attachment (feedback); cloud custom uniform `texture` | Not tested |

The adapter checks cover terrain, water, entity/block/hand-family and cloud fragments. The BSL entity fragment using Iris' `varying vec4 color` is accepted and bridged to VulkanMod's vertex color. `noisetex` is accepted across this source matrix, the BSL cloud `eyeBrightnessSmooth` blocker is resolved, and cloud `fogColor`/fog-range uniforms use live VulkanMod values. `shadowtex0` is exposed to supported entity, block entity, hand, and text programs when a pack provides a shadow pass; Veyra binds the actual shadow depth view through VulkanMod's `Sampler3` slot. `sampler2DShadow` helper parameters are lowered to the raw-depth sampler used by Veyra's software comparison bridge. A separate pre-translucent shadowtex1 is exposed to supported terrain/entity-family and post fragments. Unused sampler declarations, including `colortex0` when the shader never reads it, are removed before adapter validation; active reads from unsupported samplers still reject that program. These source results do not confirm sampler descriptors, lighting, image layouts, or rendered output. Other unsupported custom uniforms and samplers remain; see each row's generated rejection details in the machine-readable report.

The exact source URLs and SHA-512 checksums are in [validation/shaderpack-inputs.json](validation/shaderpack-inputs.json); declared profile selections are listed in [validation/shaderpack-profile-matrix.json](validation/shaderpack-profile-matrix.json). Default results and profile results are in [validation/shaderpack-source-audit.json](validation/shaderpack-source-audit.json) and [validation/shaderpack-profile-audit.json](validation/shaderpack-profile-audit.json). CI downloads the archives temporarily and uploads audit JSON, checksums and logs; the packs remain under their authors' licenses and are not included in Veyra.

The declared-profile matrix covers 24 dimension cases for each Complementary edition and 15 for BSL (63 cases total); all 4,429 profile-selected stage sources preprocess. Across all 75 cases, the geometry adapters accept 18 and reject 699 fragment-program/configuration combinations. The accepted cases are BSL only; its other tested geometry fragments still fail adapter checks. Active compute preflight rejects 9 of 24 profile/dimension cases for each Complementary edition. BSL's five profiles keep the inventoried compute stage inactive; Sildur's declares no profiles in this input. All 75 cases have `visualValidation: false`. This covers declared profiles, not arbitrary option combinations.

### Beta.4 CI source audit

PR #5's [source audit run](https://github.com/glebKovshov/Veyra/actions/runs/39300268808) passed for the same four pinned packs: 12 default cases and 63 declared-profile/dimension cases, with 18 geometry fragment programs accepted and 699 rejected. The aggregate does not establish complete pipeline support; remaining sampler, attachment, compute and geometry interfaces still prevent full-pack confirmation. All 12 Lavapipe fixtures passed in [graphics run](https://github.com/glebKovshov/Veyra/actions/runs/39300269051), but they use synthetic fixtures rather than third-party packs. Every pinned pack remains `Not tested` in-game.

### Beta.5 CI and release

PR #6's final [Build](https://github.com/glebKovshov/Veyra/actions/runs/39305176030), [pinned source audit](https://github.com/glebKovshov/Veyra/actions/runs/39305175917), and [graphics smoke](https://github.com/glebKovshov/Veyra/actions/runs/39305175988) passed before merge. The audit again covered 12 default and 63 declared-profile/dimension cases, accepting 18 and rejecting 699 geometry fragment programs. It does not evaluate complete cloud vertex/fragment pipelines. All 12 Lavapipe fixtures passed as synthetic runtime tests; every pinned pack remains `Not tested` in-game.

Beta.5's [tag Release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39306047963) passed the same source/runtime gates, built and verified the distributable, then published [the prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.5%2Bmc1.21.11). This release does not certify a third-party shaderpack as playable.

## Reproduce source checks

Download the exact input versions legally from their source and verify the recorded checksums. With JDK 21, run:

```sh
./gradlew auditShaderPacks -PshaderPacks=/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=-1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--profile=PROFILE_NAME,/absolute/path/pack.zip
```

The profile audit option selects one declared profile. The default matrix and separate profile report distinguish those selections; neither establishes that arbitrary options or complete pipelines render correctly. The same pinned source audit can be rerun from the repository's GitHub Actions workflow.

## Unverified integration checks

The [beta.2 release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39289684005) passed all 12 Lavapipe fixtures with runtime Vulkan validation checks enabled. These fixtures exercise Veyra's synthetic render paths and do not render any third-party pack. Physical GPU testing, F6 UI interaction, popular-pack visual compatibility, water/sky/light/transparency, resize, resource reload, world/dimension/pack switching and sustained GPU-memory checks remain unverified. Release `0.2.0-beta.2` is a prerelease and makes no full Iris parity claim.

## Water opaque-depth bridge (after alpha.1)

`gbuffers_water` now samples the separately captured `depthtex1` image with nearest/clamp addressing. Opaque and shadow terrain still reject this sampler. Before auxiliary-color inputs were added, Complementary water sources advanced to `gaux2` (legacy alias for `colortex5`); total acceptance at that historical checkpoint remains 18/717 because other active resources are still unsupported.

At `51c5875`, [graphics run 39208570838](https://github.com/glebKovshov/Veyra/actions/runs/39208570838) passed both original fixtures: MRT 222,983/0 magenta pixels and water-depth 222,757/0, with zero runtime validation errors. The water predicate requires opaque depth behind the water fragment and rejects far-depth clears. Teardown remains unresolved (10,031 diagnostics for MRT, 10,050 for water; the additional 19 are VkPipeline objects, ownership unproven). This is synthetic runtime evidence, not third-party-pack visual compatibility. These changes are newer than the published alpha.1 tag.

## Water auxiliary color inputs (after alpha.1)

Water geometry now reads up to six distinct auxiliary colortex1..15 images, including gdepth/gnormal/composite/gaux aliases. Aliases share one native descriptor. The binding follows the current buffer side after deferred, and graphics-ordered barriers separate attachment writes from sampling. Read-only inputs are allocated/initialized even without a writer; their dimensions may differ from the scene. Reading the scene color or an image attached to the same water draw remains unsupported and produces a diagnostic before drawing. Sampler behavior is nearest/clamp at the available base level; general Iris sampler/mipmap properties remain incomplete.

The original water-color-inputs fixture writes colortex4 in opaque MRT, changes colortex5's ping-pong side in deferred, and checks a half-resolution read-only colortex7 clear value. Its first software Vulkan run passed with 222,757 magenta pixels versus 0 disabled and no runtime validation errors. The existing MRT and water-depth checks also passed. Current exact-commit evidence is recorded in VALIDATION.md; this is not a third-party-pack result.

The complete local 75-case source matrix exactly matches downloaded CI run 39249400296 at that historical checkpoint: 5,178 sources, 18 accepted / 699 rejected. Complementary water advances from `shadowtex1` to `blockEntityId`; BSL water advances from `shadowtex1` to `shadowFade`. Sildur's water requires defined image-feedback behavior in addition to the still-missing `colortex4` inputs in other geometry bridges.
