# 0.2 development validation status

## 0.2.0-beta.2 candidate — local checks

The candidate adds Iris `shadowFade` as a frame-captured scalar uniform and keeps BSL's `uniform.float.shadowFade` expression authoritative when the pack defines it. A forced local JUnit run passed **159 tests** with zero failures/errors/skips. The clean JDK 21 run of `clean test validateShaders build packageExamplePacks` passed; the 159-test suite has zero failures/errors/skips. shaderc preprocessed 39 example shaders and compiled 36 directly to Vulkan SPIR-V, plus four conditional-include checks, entity shadow-sampler checks, and the unbound-output dataflow compile. The rebuilt installable JAR passed `scripts/verify_jar.py` for Minecraft 1.21.11 and VulkanMod 0.6.8. The pinned source audit completed all 75 default/profile/dimension cases and preprocessed 5,178 sources; the adapters accepted 18 and rejected 699 geometry fragment combinations. The source-only audit now passes BSL's `shadowFade` stage but still reports per-draw entity IDs, cloud texture, and a source-only `BIOME_DESERT` registry lookup.

The first push/PR graphics-smoke runs on `c4e74c8` exercised the extended custom-uniform fixture and exposed a terrain-provider failure: `Unknown shader pack float uniform shadowFade`. The post provider already resolved the override, but the terrain provider did not. A shared resolver now applies the frame fallback or pack override in both paths and rejects non-float overrides. The clean local build, 159 tests and JAR verifier pass on this fix; fresh Build, source-audit and graphics-smoke runs are pending.

## Post shadow inputs and borrowed-depth ID fix (after alpha.1)

Runtime commit `317fb00b3a7387134826317320b4b9947fddf887` passes [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39249394264), [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39249400344), [push graphics](https://github.com/glebKovshov/Veyra/actions/runs/39249394235), [PR graphics](https://github.com/glebKovshov/Veyra/actions/runs/39249400249) and [source audit](https://github.com/glebKovshov/Veyra/actions/runs/39249400296). Downloaded XML confirms **139 tests, zero failures/errors/skips**. shaderc preprocesses 38 examples and compiles 35 directly to SPIR-V, plus post/geometry comparison helpers and conditional/dataflow checks. The downloaded CI mod JAR passes the verifier.

| Fixture | Active / disabled magenta pixels | Runtime errors | Teardown diagnostics |
| --- | --- | --- | --- |
| Terrain MRT | 222757 / 0 | 0 | 10031 |
| Water opaque depth | 222757 / 0 | 0 | 10049 |
| Water color inputs | 222757 / 0 | 0 | 10050 |
| Specular atlas | 222577 / 0 | 0 | 10031 |
| Specular entity | 18410 / 0 | 0 | 10035 |
| Shadow color terrain | 222496 / 0 | 0 | 10032 |
| Shadow color entity | 18293 / 0 | 0 | 10356 |
| Separate shadow depth, water | 226902 / 0 | 0 | 10370 |
| Separate shadow depth, entity | 16358 / 0 | 0 | 10053 |
| Four-phase post shadow chain | 230400 / 0 | 0 | 10034 |

All ten exact-commit graphics artifacts were downloaded. The new active/disabled screenshots were inspected. Prepare/deferred/composite/final each require authored shadow color and separate depths, then pass sentinel data through colortex4/5/6. Modern float and legacy vec4 helpers, unused noisetex headers and 60,000 blank lines per post source are included. The first run failed without VUID errors: descriptor evidence showed the opaque-depth sampler reading Light Texture. Correcting reversed usage/native-ID arguments in the borrowed VkGpuTexture constructor makes all four phases bind the actual snapshot. The predicate and 2000-pixel threshold were preserved.

All 75 downloaded source-audit records match local and committed reports: 5,178 sources, 18 accepted / 699 rejected, visualValidation=false throughout. That audit preprocesses selected stages and checks geometry adapters; it does not validate complete post pipelines. The original GPU chain covers this post path separately. Teardown ownership, physical GPU/real-pack visuals and lifecycle stress remain incomplete; stable integration/release is not ready.

## Historical separate shadow depth validation (after alpha.1)

Runtime commit `cca001973c906da89f461c30998e121a88e27405` passes [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39246874678), [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39246882115) and [push graphics](https://github.com/glebKovshov/Veyra/actions/runs/39246874812). Downloaded CI XML confirms **131 tests, zero failures/errors/skips**; the downloaded mod JAR passes the verifier. shaderc preprocesses 36 examples and compiles 33 directly to SPIR-V, plus both shadow comparison helper fixtures and existing conditional/dataflow checks.

| Fixture | Active / disabled magenta pixels | Runtime errors | Teardown diagnostics |
| --- | --- | --- | --- |
| Terrain MRT | 221976 / 0 | 0 | 10031 |
| Water opaque depth | 222757 / 0 | 0 | 10050 |
| Water color inputs | 222757 / 0 | 0 | 10370 |
| Specular atlas | 222590 / 0 | 0 | 10031 |
| Specular entity | 18293 / 0 | 0 | 10035 |
| Shadow color terrain | 222445 / 0 | 0 | 10032 |
| Shadow color entity | 18410 / 0 | 0 | 10676 |
| Separate shadow depth, water | 226902 / 0 | 0 | 10049 |
| Separate shadow depth, entity | 16456 / 0 | 0 | 10053 |

All nine exact-commit graphics artifacts were downloaded; both new active screenshots were inspected. The new fixtures enable shadowTranslucent explicitly and author opaque depth 0.75 and water depth 0.25. A main draw must read both values from separate shadowtex1/shadowtex0 images at the same coordinate; an alias, late snapshot or cleared texture cannot pass. Initial runs exposed missing fixture translucency and a real producer depth-layout error after entity features; the failing evidence is preserved in WORK_SUMMARY.md. Final graphics validation has no runtime errors; process teardown ownership is still unresolved. This verifies original synthetic scenes, not third-party shaderpack lighting or physical GPUs.

The fresh local source audit processes all 75 configurations: 5,178 sources, 18 accepted / 699 rejected. Complementary now reaches blockEntityId; BSL reaches entityId/blockEntityId/shadowFade. All 75 local and committed JSON records match the downloaded [exact-commit source CI](https://github.com/glebKovshov/Veyra/actions/runs/39246874561), with visualValidation=false throughout. [PR source audit](https://github.com/glebKovshov/Veyra/actions/runs/39246882138) and [PR graphics](https://github.com/glebKovshov/Veyra/actions/runs/39246882139) also pass. Post-program shadowtex1, wider sampler properties, real-pack visual validation and lifecycle stress remain incomplete; stable integration/release is not ready.

## Historical shadow color validation (after alpha.1)

Runtime commit `ad69519` passes [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39215900899), [push graphics](https://github.com/glebKovshov/Veyra/actions/runs/39215900818) and [PR graphics](https://github.com/glebKovshov/Veyra/actions/runs/39215906459). Downloaded CI XML confirms **122 tests, zero failures/errors/skips**. shaderc preprocesses 31 example sources and compiles 28 directly to SPIR-V. The downloaded mod JAR passes the verifier.

| Fixture | Active / disabled magenta pixels | Runtime errors | Teardown diagnostics |
| --- | --- | --- | --- |
| Terrain MRT | 223272 / 0 | 0 | 10031 |
| Water opaque depth | 222757 / 0 | 0 | 10049 |
| Water color inputs | 222757 / 0 | 0 | 10049 |
| Specular atlas | 222561 / 0 | 0 | 10671 |
| Specular entity | 18410 / 0 | 0 | 10035 |
| Shadow color terrain | 222509 / 0 | 0 | 10672 |
| Shadow color entity | 18532 / 0 | 0 | 10036 |

All seven exact-commit artifacts were downloaded. Both new screenshots were inspected. The shadow fixtures require the authored color written by shadow.fsh through both modern and legacy sampler names; default/clear images cannot pass. CPU tests cover missing shadow pass/feedback, sampler types, unused declarations and collision with six auxiliary water inputs. Runtime-green does not resolve teardown ownership or establish third-party-pack visual support. All 75 local and committed source-audit records match the downloaded [exact-commit source CI](https://github.com/glebKovshov/Veyra/actions/runs/39215900786): 5,178 sources, 18 accepted / 699 rejected, visualValidation=false throughout. Complementary now reaches shadowtex1 or blockEntityId.

## Historical static specular validation (after alpha.1)

Runtime/test commit `3c1d749c69fbd33b6e6e43ab4b684bf3c551ae9a` passes [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39214797686), [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39214805186), [push graphics](https://github.com/glebKovshov/Veyra/actions/runs/39214797662) and [PR graphics](https://github.com/glebKovshov/Veyra/actions/runs/39214805071). Downloaded XML confirms 115 tests with zero failures/errors/skips; the downloaded CI mod JAR passes the verifier.

| Fixture | Active / disabled magenta pixels | Runtime validation errors | Teardown diagnostics |
| --- | --- | --- | --- |
| Terrain MRT | 223595 / 0 | 0 | 10031 |
| Water opaque depth | 222757 / 0 | 0 | 10370 |
| Water color inputs | 222757 / 0 | 0 | 10049 |
| Static specular atlas + mip level 2 | 222914 / 0 | 0 | 10031 |
| Static entity specular + separate albedo | 18532 / 0 | 0 | 10355 |

Local `test validateShaders build` passes with 115 JUnit tests, zero failures/errors, 28 example preprocesses and 25 direct SPIR-V compilations. The distributable verifier passes. All 75 local source-audit records match the downloaded [exact-commit source CI](https://github.com/glebKovshov/Veyra/actions/runs/39214805068) at `3c1d749c69fbd33b6e6e43ab4b684bf3c551ae9a`: 5,178 sources, 18 accepted / 699 rejected. Complementary now reaches shadowcolor0 or blockEntityId.

The initial material-only [graphics matrix](https://github.com/glebKovshov/Veyra/actions/runs/39213650838) passes: atlas 223,051/0 active/disabled magenta pixels, entity 4,688/0, runtime validation errors zero for both downloaded artifacts. The atlas fixture requires mip level 2 and at least three image levels. Screenshots were inspected. Teardown diagnostics remain 10,351/10,355 respectively; ownership is unresolved.

The later entity predicate also reads albedo and requires it to differ from the material. Its first run failed because the subject was outside the randomized player camera, established from login coordinates and screenshots. Commit `3c1d749` places the subject relative to the actual player position; the fresh run above passes the stronger predicate. Its screenshot was inspected. The threshold remains 2000 pixels and the disabled baseline requirement is unchanged. The material shadow binding is implemented but has no dedicated PBR-shadow GPU fixture yet.

Static specular support does not include normal maps or material animation. Physical GPU, real-pack visual validation and long lifecycle stress remain unverified. PR #2 stays draft; stable release is not ready.

## Historical water-color validation (after alpha.1)

Runtime code `ef6fb4413d4ec8831e8b3c92fae2c9db6467bd46` passes [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39210665983), [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39210672564), [push graphics](https://github.com/glebKovshov/Veyra/actions/runs/39210665863), [PR graphics](https://github.com/glebKovshov/Veyra/actions/runs/39210672561), [push source audit](https://github.com/glebKovshov/Veyra/actions/runs/39210665829) and [PR source audit](https://github.com/glebKovshov/Veyra/actions/runs/39210672681).

Downloaded CI reports confirm 106 JUnit tests with zero failures/errors/skips. shaderc preprocesses 25 example sources and compiles 22 directly to Vulkan SPIR-V, plus the existing compiler fixtures. The downloaded mod JAR passes the distributable verifier. All 75 downloaded source-audit records exactly match the complete local run (5,178 sources, 18 accepted / 699 rejected); both committed reports have been refreshed.

In the exact push graphics run, MRT gives 222,784/0 active/disabled magenta pixels, water-depth 222,757/0 and water-color-inputs 222,757/0; all have zero runtime validation errors. The new fixture requires opaque MRT colortex4, the post-deferred ping-pong side of colortex5, both modern/legacy aliases, and a half-resolution read-only colortex7 with its declared clear value. It also declares an unused noisetex to catch descriptor-binding drift. Active/disabled screenshots were inspected.

Teardown diagnostics are 10,031 / 10,049 / 10,370 respectively, and vary across runs. Ownership remains unresolved. The native Computer Use pipe is still unavailable; physical GPU/UI and third-party-pack visual support are unverified. PBR inputs, the second shadow depth map, other geometry color-input bridges and defined attachment-feedback behavior remain blockers. Stable 0.2 is not ready; published alpha.1 predates these changes.
## Historical water-depth validation (after alpha.1)

Runtime code commit `51c5875949259444b465409828ad19cbd6557006` passes the [push Build](https://github.com/glebKovshov/Veyra/actions/runs/39208570869), [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39208577331), [push graphics matrix](https://github.com/glebKovshov/Veyra/actions/runs/39208570838) and [PR graphics matrix](https://github.com/glebKovshov/Veyra/actions/runs/39208577387).

- 96 JUnit tests, zero failures/errors/skips; 23 example sources preprocessed, 20 compiled directly to Vulkan SPIR-V, conditional and shadow helper fixtures pass. The remapped JAR verifier passes.
- Original terrain MRT fixture: 222,983 active versus 0 disabled magenta pixels, 0 runtime validation errors, 10,031 teardown diagnostics.
- New water opaque-depth fixture: 222,757 versus 0 pixels, 0 runtime validation errors, 10,050 teardown diagnostics. It requires copied opaque depth behind each water fragment, excluding cleared far-depth; screenshots were downloaded and inspected.
- The first test exposed sparse descriptor binding failure; the next exposed borrowed main depth pointing at the copy pass's own color output. Both were fixed before this passing result. The readback threshold was preserved.
- The 75 local source-audit records exactly match the downloaded [CI source audit](https://github.com/glebKovshov/Veyra/actions/runs/39208129938): 5,178 sources, 18 accepted / 699 rejected. Complementary water now advances to its unsupported `gaux2` sampler.

The additional 19 teardown diagnostics are VkPipeline objects; ownership is not established. These checks do not establish third-party-pack visual compatibility, clean teardown, physical-GPU support or long-session stability. The published alpha.1 tag predates these fixes. Stable 0.2 remains pending.
## Local continuation checks — 2026-10-19

On top of `e42f151`, JDK 21.0.12.1 ran `test --rerun-tasks` (92 tests, zero failures/errors/skips) and `build validateShaders packageExamplePacks` successfully. `scripts/verify_jar.py build/libs/veyra-0.2.0+mc1.21.11.jar` verified the remapped JAR, metadata, mixins, nested JCPP and exclusion of the graphics harness. Historical JARs remain in the local build directory; an explicit path avoids selecting stale artifacts.

The include expander now bounds total recursive work, in addition to depth and output size. The pinned audit accepts a JSON argument file to avoid Windows command-length limits. Source audit and Lavapipe workflows are reusable release prerequisites and run on relevant pull requests. The audit records unsupported features; a green audit job is not a claim that those packs run.

Windows UI validation is blocked: `sky.list_apps()` returned `Computer Use native pipe is unavailable: failed to connect native pipe: Не удается найти указанный файл. (os error 2)`. No current physical-GPU/UI compatibility result is claimed. The prior graphics and source results below are historical and retain their original source commit attribution.


At the historical commit below, the feature branch passed the full Fabric Loom build, tests, remapping and distributable verification at commit `340db0ca80010d42591383b13e12d76463467048` in both the [push run](https://github.com/glebKovshov/Veyra/actions/runs/39166689216) and the [PR run](https://github.com/glebKovshov/Veyra/actions/runs/39166691696). This produces a development artifact, not a stable release.

- At that historical commit, 74 JUnit tests pass with no failures or skips, including pinned VulkanMod bytecode contracts, draw-buffer mappings, per-target blend properties, moon phase uniforms and the entity-color bridge.
- At that historical commit, native shaderc checks preprocess 19 examples and compile 16 directly to SPIR-V. Four conditional-include compiler checks and an unbound-output dataflow compiler check also pass. These checks do not establish descriptor/runtime correctness.
- Four pinned external packs were audited in the [source-audit run](https://github.com/glebKovshov/Veyra/actions/runs/39166689354): 12 default cases plus 63 declared-profile/dimension cases. All 5,178 selected stage sources preprocess. The geometry adapters accepted 18 and rejected 699 fragment/configuration combinations; none of the packs has a complete working pipeline. See COMPATIBILITY.md for the per-pack failures.
- A new isolated test mod launches Minecraft 1.21.11 with VulkanMod 0.6.8, an original generated flat world, Mesa Lavapipe and the Vulkan validation layer. It compares the fixture's auxiliary MRT color against disabled shaders and uploads images, logs and a machine-readable result. The harness is excluded from the distributed mod.
- [Lavapipe graphics run 39166399772](https://github.com/glebKovshov/Veyra/actions/runs/39166399772) passed the synthetic MRT/composite check: the captured active frame contains 222,934 magenta pixels and the shaders-disabled baseline contains 0. No Vulkan validation errors occurred during runtime. The run still reports 10,031 `VUID-vkDestroyDevice-device-05137` diagnostics during whole-process device teardown; these remain in the artifact and do **not** establish clean Vulkan resource teardown.
- Physical GPU testing, UI interaction, resize/reload stress and popular-pack visual compatibility remain unconfirmed. The Lavapipe fixture confirms its small composite/MRT test path only; it is not evidence of general shaderpack compatibility. No full-parity stable release is justified.

## Previous 0.1.1 release checks

The mod passes the full GitHub Actions build. It has not been tested in a running Minecraft client.

## Completed

- Full Fabric Loom build, JUnit tests, CPU shader validation, remapping, and installable JAR upload passed on GitHub Actions: https://github.com/glebKovshov/Veyra/actions/runs/39131591847 (commit `4120c780dc1af3a5bc0504c0a03366bb3e323ed7`). The release workflow repeats these checks for the final release commit.

- JDK 21 compilation of the production pack loader, include expander, settings records, and all GLSL adapters.
- 35 JUnit regression tests passed, with no failures or skips. Coverage includes numeric pass ordering, wrapped ZIPs, active-selection preservation, failed configuration writes, include expansion limits, path traversal rejection, comments, and 18 bundled geometry examples.
- The new `validateShaders` Gradle task passed in an isolated Java build using the actual production classes. shaderc preprocessed 18 examples and compiled 15 directly to Vulkan SPIR-V with automatic location/binding assignment. Three examples need VulkanMod's separate uniform/descriptor conversion and were only preprocessed, not compiled to SPIR-V by this check.
- Four shaderc compiler checks passed: inactive missing include accepted, active missing include rejected, guarded recursion accepted, unguarded recursion rejected.
- Both GitHub Actions workflows parsed as YAML, and their shell steps passed `bash -n`.
- `git diff --check` passed.

## Validation limits

The local editing sandbox denies Fabric Loom's optional Unix-socket probe, so the full Minecraft integration build and software-Vulkan run were verified in GitHub Actions. The Lavapipe harness tests only Veyra's small generated MRT/composite fixture; it does not establish popular-pack compatibility or general descriptor correctness. Physical GPU validation remains necessary.

Verify in Minecraft 1.21.11 with Fabric Loader 0.18.4+ and VulkanMod 0.6.8: pack selection, refresh after edits/removal, disabling, resource reload, window resizing, world changes, depth snapshots, and shadows on the target GPU. Review logs and visual output.

## Reproduce

Run `./gradlew --no-daemon build validateShaders packageExamplePacks` in a normal JDK 21 environment. Push a branch named `release/` plus `mod_version`, or a matching version tag, to run the gated Release workflow. It uploads the mod, sources, example packs, and SHA-256 checksums after all checks succeed.

## Alpha release validation — 2026-10-19

Tag `v0.2.0-alpha.1+mc1.21.11` points to `2b5acb0ddc823443e42733ae712803d78982d986`. The local alpha build and explicit JAR verifier pass. The complete Python source-audit runner was executed successfully on Windows: 12 default + 63 profile/dimension cases, 5,178 preprocessed sources, 18 accepted / 699 rejected geometry fragment/configuration cases. The new include limit introduces no failures in this matrix.

The graphics prerequisite in [release run 39206709151](https://github.com/glebKovshov/Veyra/actions/runs/39206709151) passed on that exact tag. Its downloaded evidence reports 222,926 active magenta pixels, 0 with shaders disabled, 0 runtime validation errors and 10,031 teardown diagnostics. This confirms the synthetic MRT/composite fixture only. Popular-pack visual compatibility and clean device teardown remain unconfirmed.

The already-published 0.1.1 line was independently rebuilt in a detached worktree and fast-forwarded into `main` at `e37f188`; GitHub confirms PR #1 merged. PR #2 remains draft for the incomplete 0.2 feature line.

The alpha prerelease is now [published](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-alpha.1%2Bmc1.21.11); the complete release workflow succeeded. All three published assets were downloaded again and matched the published SHA-256 checksums. The downloaded installable JAR passed `scripts/verify_jar.py`; its SHA-256 is `e421f600972bd7d6e8ed509d7c4a0f134c440c7a4faefab2d75679230c7552a6`. This remains an experimental supported subset, not stable full-Iris compatibility.
