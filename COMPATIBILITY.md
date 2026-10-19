# Shaderpack compatibility: development 0.2

## Iris uniforms and expression semantics (beta.6)

- Iris' hardcoded `velocity` uniform and additional Iris common, dimension, player, celestial, and hardcoded custom uniforms are provided to the supported terrain, entity-family, and post uniform paths. Pack definitions override Iris hardcoded custom uniforms where Iris allows it.
- Eligible non-opaque uniform declarations Iris does not register are lowered to their GLSL initializer or zero, matching their value under Iris instead of being mistaken for Veyra-provided inputs. Unsupported registered uniforms still reject the program.
- Custom-uniform `smooth()` now follows Iris' `SmoothFloat`: fade arguments are half-lives in tenths of a second (default 1), zero follows immediately, and a constant first argument is OptiFine's id. `fmod` is floored as in Iris; `edge`, `log(base, x)`, `log10`, `exp10`, `random` and `randomInt` were added.
- An undefined `BIOME_*` constant (Complementary r5.9.3 references `BIOME_SULFUR_CAVES`, which vanilla 1.21.11 does not register) fails in Iris too: Iris warns and never sets the uniform, so shaders read 0. Veyra now mirrors that for such definitions and their dependents and logs the same kind of warning. Inputs that Iris provides but Veyra does not still reject the program.
- The offline source audit seeds vanilla biome ids from Minecraft's `Biomes` class in the same order `BiomesMixin` records at runtime, so `BIOME_*` constants no longer produce audit-only rejections.

The refreshed pinned source audit on PR #11 head `cc56216` (75 cases, all 5,178 stages preprocessed) accepted 72 geometry fragment programs and rejected 645; every case has `visualValidation: false`. The overall counts did not change because the newly supported aliases occur in programs with other unsupported inputs. Default Overworld blockers per program:

| Pack | Default geometry fragments accepted / attempted (OW, Nether, End) | Remaining default blockers | In-game pack result |
| --- | --- | --- | --- |
| Complementary Reimagined / Unbound r5.9.3 | 1/10, 1/10, 1/10 | terrain/water: `signMidCoordPos` (requires unavailable `mc_midTexCoord`); entity/block/hand/spidereyes: `currentRenderedItemId`; cloud adapter accepts observed `colortex0` and `noisetex` reads; selected profiles: `shadowcomp.csh` compute | Not tested |
| BSL 10.1.8 | 1/9, 1/9, 1/9 | terrain: an entity-metadata, midpoint-UV, view-space, or tangent input grouped by the adapter diagnostic; water: `mat`; entities: `entityColor`; hand: `isMainHand` varying; block: unsupported entity-bridge inputs; clouds: `texture` sampler | Not tested |
| Sildur's Vibrant 2.02 Extreme | 0/5, 0/5, 0/5 | `colortex4` in translucent block/entity/hand geometry; water reads `colortex4` while it is an attachment; clouds: `texture` sampler | Not tested |

PR #11 adds the terrain aliases `lmCoord` (VulkanMod lightmap input, location 5) and `color` (VulkanMod vertex color input, location 0); the refreshed audit no longer reports either alias as a blocker. Complementary terrain/water now stop at `signMidCoordPos`, which depends on `mc_midTexCoord`; BSL terrain advances past `color` but still hits an unsupported vertex input. The audit accepted-count did not increase because the full programs contain additional missing inputs. The cloud adapter bridges the scene-color and noisetex sampling calls observed in the pinned default programs. Uniform declarations that the adapters cannot bind are named in the diagnostic.

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
