# Shaderpack compatibility: development 0.2

## Static material inputs (development)

The geometry bridges now read resource-pack `_s.png` specular maps for terrain/shadow atlases and standalone entity textures. Atlas rectangles follow the live albedo sprites, including edge padding. Data use straight UNORM channels, with linear or LabPBR category-aware mipmaps selected by `optifine/texture.properties`. Missing maps use the zero specular default. Animated material maps and normal maps remain unsupported; animated specular inputs produce an explicit error. General Iris filtering/mipmap settings are incomplete.

The current local 75-case source audit at candidate parent `d7036d9` includes the scalar custom-uniform and `shadowFade` changes. It preprocesses 5,178 sources; geometry adapters accept 18 and reject 699 cases. `shadowFade` is no longer a BSL blocker. The matrix is source-only and does not establish popular-pack visual compatibility. Original atlas/entity fixtures validate material data separately.

## Completed shadow color inputs

Terrain and entity-family fragments read the existing completed shadow color attachment through `shadowcolor0` and the legacy `shadowcolor` alias. Both aliases share one descriptor. Sampling is linear/clamp at base level; general shadow color format/mipmap properties, additional attachments are incomplete. Terrain reserves native slot 11, leaving at most five distinct auxiliary water inputs when shadow color is active. Missing shadow passes, shadow attachment feedback and slot collisions fail explicitly. Original terrain/entity GPU fixtures read authored shadow output with zero runtime validation errors; see VALIDATION.md. The separate depth map described below is also available; neither path verifies a complete third-party pack.

## Separate pre-translucent shadow depth

Terrain and entity-family fragments can read `shadowtex1` as sampler2D or sampler2DShadow with the software comparison bridge. It is a separate depth image copied after opaque terrain and entity/block features, before translucent terrain. Sampling is nearest/clamp at base level. Shadow attachment feedback fails explicitly. Terrain reserves native slot 10; water auxiliary assignment skips occupied slots 10/11, allowing five inputs with shadowtex1 alone or four with both shadowtex1 and shadow color. GPU fixtures require different authored values at the same coordinate in shadowtex0/shadowtex1, so aliases and late snapshots cannot pass. Post programs also read these images as described below; the context-dependent legacy shadow alias and general sampler/mipmap properties remain incomplete.

## Post shadow inputs

Prepare, deferred, composite and final programs read actual shadowtex0/shadowtex1 and shadowcolor0/shadowcolor views. Color aliases share one descriptor. Unused declarations do not reserve resources; invalid sampler types, duplicates, missing shadow passes, unsupported shadow names and more than twelve total descriptors produce errors before drawing. The original four-phase GPU fixture passes sentinel data through colortex4/5/6 and requires distinct depth values plus authored shadow color at every phase. It covers modern float and legacy vec4 comparison helpers, an unused noisetex header and 60,000 blank lines per post source. Wider texture operations, mip/filter properties and the context-dependent shadow alias remain incomplete. These synthetic checks do not establish third-party-pack visual support.

## Verified source checkpoint

This is a **source-only compatibility audit**. It does not compile complete shaderpack pipelines or render third-party packs. The pinned archives were checked locally for this candidate: 12 default configurations and 63 declared-profile/dimension configurations. All 5,178 selected stage sources were discovered and preprocessed. The adapters accepted 18 and rejected 699 geometry fragment/configuration combinations. None of the listed packs has a complete pipeline confirmed in-game. GitHub CI evidence for this candidate will be recorded after its workflows finish.

| Pack | Version | Options / profiles | Default stages preprocessed, Overworld / Nether / End | Default geometry fragments accepted / attempted | Current source blockers | In-game pack result |
| --- | --- | --- | --- | --- | --- | --- |
| Complementary Reimagined | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Custom `blockEntityId`, terrain/water `velocity`, cloud `fogColor`; `shadowcomp.csh` compute in selected profiles | Not tested |
| Complementary Unbound | r5.9.3 | 403 / 8 | 73/73, 73/73, 73/73 | 0 / 10 | Custom `blockEntityId`, terrain/water `velocity`, cloud `fogColor`; `shadowcomp.csh` compute in selected profiles | Not tested |
| BSL | 10.1.8 | 383 / 5 | 63/63, 59/59, 63/63 | 1 / 9 | Custom `entityId` / `blockEntityId`; cloud `texture` uniform; source-only evaluator lacks registered `BIOME_DESERT` for `isDesert` | Not tested |
| Sildur's Vibrant | 2.02 Extreme | 115 / 0 | 42/42, 42/42, 42/42 | 0 / 5 | `colortex4` in translucent entity/block/hand geometry; water reads colortex4 while it is also an attachment (feedback); cloud custom uniform `texture` | Not tested |

The adapter checks cover terrain, water, entity/block/hand-family and cloud fragments. The BSL entity fragment using Iris' `varying vec4 color` is accepted and bridged to VulkanMod's vertex color. `noisetex` is accepted across this source matrix, and the BSL cloud `eyeBrightnessSmooth` blocker is resolved in the current code. `shadowtex0` is now exposed to supported entity, block entity, hand, and text programs when a pack provides a shadow pass; Veyra binds the actual shadow depth view through VulkanMod's `Sampler3` slot. `sampler2DShadow` helper parameters are lowered to the raw-depth sampler used by Veyra's software comparison bridge. A separate pre-translucent shadowtex1 is exposed to supported terrain/entity-family and post fragments. Unused sampler declarations, including `colortex0` when the shader never reads it, are removed before adapter validation; active reads from unsupported samplers still reject that program. These source results do not confirm sampler descriptors, lighting, image layouts, or rendered output. Other unsupported custom uniforms and samplers remain; see each row's generated rejection details in the machine-readable report.

The exact source URLs and SHA-512 checksums are in [validation/shaderpack-inputs.json](validation/shaderpack-inputs.json); declared profile selections are listed in [validation/shaderpack-profile-matrix.json](validation/shaderpack-profile-matrix.json). Default results and profile results are in [validation/shaderpack-source-audit.json](validation/shaderpack-source-audit.json) and [validation/shaderpack-profile-audit.json](validation/shaderpack-profile-audit.json). CI downloads the archives temporarily and uploads audit JSON, checksums and logs; the packs remain under their authors' licenses and are not included in Veyra.

The declared-profile matrix covers 24 dimension cases for each Complementary edition and 15 for BSL (63 cases total); all 4,429 profile-selected stage sources preprocess. Across all 75 cases, the geometry adapters accept 18 and reject 699 fragment-program/configuration combinations. The accepted cases are BSL only; its other tested geometry fragments still fail adapter checks. Active compute preflight rejects 9 of 24 profile/dimension cases for each Complementary edition. BSL's five profiles keep the inventoried compute stage inactive; Sildur's declares no profiles in this input. All 75 cases have `visualValidation: false`. This covers declared profiles, not arbitrary option combinations.

## Reproduce source checks

Download the exact input versions legally from their source and verify the recorded checksums. With JDK 21, run:

```sh
./gradlew auditShaderPacks -PshaderPacks=/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=-1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--dimension=1,/absolute/path/pack.zip
./gradlew auditShaderPacks -PshaderPacks=--profile=PROFILE_NAME,/absolute/path/pack.zip
```

The profile audit option selects one declared profile. The default matrix and separate profile report distinguish those selections; neither establishes that arbitrary options or complete pipelines render correctly. The same pinned source audit can be rerun from the repository's GitHub Actions workflow.

## Remaining release checks

GitHub CI's [Lavapipe client run 39183912225](https://github.com/glebKovshov/Veyra/actions/runs/39183912225) passed Veyra's synthetic terrain-MRT/composite fixture: the active frame contained 222,319 magenta pixels and the shaders-disabled baseline contained 0. Runtime Vulkan validation errors were 0. At process teardown, VulkanMod/Minecraft reports 10,031 `VUID-vkDestroyDevice-device-05137` child-object diagnostics; ownership has not been identified, so clean device teardown is not established. The fixture does not render any third-party pack. Physical GPU testing, F6 UI interaction, popular-pack visual compatibility, water/sky/light/transparency, resize, resource reload, world/dimension/pack switching and sustained GPU-memory checks remain unverified. The published 0.1.1 release predates this development branch; no main-branch release claiming Iris parity has been published.

## Water opaque-depth bridge (after alpha.1)

`gbuffers_water` now samples the separately captured `depthtex1` image with nearest/clamp addressing. Opaque and shadow terrain still reject this sampler. Before auxiliary-color inputs were added, Complementary water sources advanced to `gaux2` (legacy alias for `colortex5`); total acceptance at that historical checkpoint remains 18/717 because other active resources are still unsupported.

At `51c5875`, [graphics run 39208570838](https://github.com/glebKovshov/Veyra/actions/runs/39208570838) passed both original fixtures: MRT 222,983/0 magenta pixels and water-depth 222,757/0, with zero runtime validation errors. The water predicate requires opaque depth behind the water fragment and rejects far-depth clears. Teardown remains unresolved (10,031 diagnostics for MRT, 10,050 for water; the additional 19 are VkPipeline objects, ownership unproven). This is synthetic runtime evidence, not third-party-pack visual compatibility. These changes are newer than the published alpha.1 tag.

## Water auxiliary color inputs (after alpha.1)

Water geometry now reads up to six distinct auxiliary colortex1..15 images, including gdepth/gnormal/composite/gaux aliases. Aliases share one native descriptor. The binding follows the current buffer side after deferred, and graphics-ordered barriers separate attachment writes from sampling. Read-only inputs are allocated/initialized even without a writer; their dimensions may differ from the scene. Reading the scene color or an image attached to the same water draw remains unsupported and produces a diagnostic before drawing. Sampler behavior is nearest/clamp at the available base level; general Iris sampler/mipmap properties remain incomplete.

The original water-color-inputs fixture writes colortex4 in opaque MRT, changes colortex5's ping-pong side in deferred, and checks a half-resolution read-only colortex7 clear value. Its first software Vulkan run passed with 222,757 magenta pixels versus 0 disabled and no runtime validation errors. The existing MRT and water-depth checks also passed. Current exact-commit evidence is recorded in VALIDATION.md; this is not a third-party-pack result.

The complete local 75-case source matrix exactly matches downloaded CI run 39249400296 at that historical checkpoint: 5,178 sources, 18 accepted / 699 rejected. Complementary water advances from `shadowtex1` to `blockEntityId`; BSL water advances from `shadowtex1` to `shadowFade`. Sildur's water requires defined image-feedback behavior in addition to the still-missing `colortex4` inputs in other geometry bridges.
