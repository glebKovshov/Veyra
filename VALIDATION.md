# Veyra 0.2 validation status

## Beta.14 candidate checks — 2026-10-20

PR #23 merged into main as 590d38b2de93761e1146fea5f2b1e06b00fba8d3 from checked head 38a4089504cdedbc2be9ba0964aa6991394cd5cb. Its Build run [39373977134](https://github.com/glebKovshov/Veyra/actions/runs/39373977134) passed build, shader validation, and distributable verification; JUnit reports contain **220 tests, 0 failures, 0 errors, and 0 skipped**. It processed 48 example shaders and compiled 45 directly to Vulkan SPIR-V.

Graphics run [39373977059](https://github.com/glebKovshov/Veyra/actions/runs/39373977059) passed all 17 Lavapipe fixtures. Entity MRT read back 18,532 active magenta pixels through `colortex1`; specular-entity read back 18,293. Runtime Vulkan validation errors were 0. Process teardown still emitted 10,035 validation diagnostics.

Pinned source audit run [39373977114](https://github.com/glebKovshov/Veyra/actions/runs/39373977114) passed for 4 packs, 12 default cases, 63 declared-profile/dimension cases, and 5,178 stages: 486 geometry fragments accepted / 231 rejected. All 75 records have `visualValidation=false`. This is source compatibility evidence; the four packs remain not visually validated in-game.

These are PR checks before the release version bump. The beta.14 tag workflow must rebuild and verify the final-version JAR before publication; no beta.14 artifact is published yet.

## Beta.13 experimental release — 2026-10-20

Published [Veyra 0.2.0-beta.13+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.13%2Bmc1.21.11) from tag `v0.2.0-beta.13+mc1.21.11`, target commit `0a275fe5d4b88aab3c033b65140b5fb48b949044`. Release workflow [39369824141](https://github.com/glebKovshov/Veyra/actions/runs/39369824141) passed the pinned source audit, all 16 Lavapipe fixtures, `build validateShaders packageExamplePacks`, distributable verification, and publication.

The tag Build run [39369823943](https://github.com/glebKovshov/Veyra/actions/runs/39369823943) passed; its uploaded JUnit reports contain **214 tests, 0 failures, 0 errors, and 0 skipped**. It preprocessed 47 examples and compiled 44 directly to Vulkan SPIR-V. The final pinned audit artifact contains 75 records across 4 packs / 5,178 stages, accepts 234 geometry fragments and rejects 483; `visualValidation=false` for all records.

Downloaded release asset `veyra-0.2.0-beta.13+mc1.21.11.jar`: **581,330 bytes**, SHA-256 `0409fd6fe226c71724426e00f2f763a81cc57a98e6350cc2a6521b9e5071c2bd`. It matches `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.13+mc1.21.11. The sources JAR and example-packs ZIP also match their published checksums.

These automated fixtures do not establish third-party pack appearance or physical-GPU correctness. No physical-GPU or third-party shaderpack visual result is claimed; the stable-release goal remains incomplete. This shell exposes no JDK for a fresh local Gradle build.

## Entity overlay and lightmap adapter candidate — 2026-10-20

The working tree now maps Iris `entityColor` to Minecraft's entity `overlayColor`, supplies the transparent fallback when the active pipeline has no overlay varying, and accepts Iris's `lmCoord` spelling. The entity adapter no longer mistakes a local `viewPos` variable for a missing fragment input. The unsupported-uniform checks now keep horizontal whitespace on one line, and unused sampler references are collected in one identifier pass.

JDK 21.0.12.1 `test validateShaders build packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed: **209 tests, 0 failures/errors/skips**; 47 example shader sources were processed and 44 compiled directly to Vulkan SPIR-V. The entity overlay, item-entity fallback, and entity lightmap alias fixtures also compiled to SPIR-V. `scripts/verify_jar.py` passed on the local beta.12 candidate JAR (578,363 bytes; SHA-256 `E2863108087BB5E4D519D053A36BB54864116F8677BE55DCCCFE2F8E6851471C`). This is a local candidate, not yet the published beta.12 asset.

The fresh pinned source audit covers 4 packs, 12 defaults, 63 declared profile/dimension cases, and 5,178 preprocessed stages. It accepts 180 geometry fragments and rejects 537; no record reports an `entityColor` or `lmCoord` error, and every record remains `visualValidation=false`. The `viewPos` false positive is gone, but the newly unblocked Complementary entity/block/hand programs require outputs beyond color attachment 0. Translucent inputs, Complementary water `viewVector`, BSL water `mat`, clouds `texture`, and Sildur's `colortex4`/feedback remain blockers. This does not establish third-party-pack rendering or stable-release readiness.

## Beta.10 published prerelease — 2026-10-20

On JDK 21.0.12.1, `test validateShaders build packageExamplePacks --rerun-tasks` passed at beta.10: **199 JUnit tests, 0 failures/errors/skips**, 43 example stages processed, 40 compiled directly to Vulkan SPIR-V, plus conditional-include, entity-ID, shadow, cloud and terrain-view-position fixtures. The remapped `veyra-0.2.0-beta.10+mc1.21.11.jar` is 571,279 bytes, SHA-256 `0cc561a569e0e2ce8d20955ec6c271b41a5e616d5adac2b006e660edcde08a8c`, and passes `scripts/verify_jar.py`.

The pinned source audit passed on final PR head `91ebcf7`: 75 cases / 5,178 sources, 126 geometry fragments accepted / 591 rejected, with `visualValidation=false` throughout. Complementary terrain and BSL terrain pass source adaptation; Complementary water still needs `viewVector`, BSL water still needs `mat`, and all three tested pack families retain other unsupported inputs. Final PR checks passed: [Build](https://github.com/glebKovshov/Veyra/actions/runs/39348939420), [source audit](https://github.com/glebKovshov/Veyra/actions/runs/39348939418), and [all 15 Lavapipe fixtures](https://github.com/glebKovshov/Veyra/actions/runs/39348939401). The `geometry-mid-texcoord` readback measured 53,648 active magenta pixels and 168,711 active opposite-midpoint pixels, versus 0 when disabled; runtime Vulkan validation errors were 0. Shutdown produced 10,031 teardown diagnostics.

Release workflow [39349523440](https://github.com/glebKovshov/Veyra/actions/runs/39349523440) passed the pinned audit, all 15 Lavapipe fixtures, Gradle build/tests, and distributable verifier, then published [beta.10](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.10%2Bmc1.21.11) as an experimental prerelease. The downloaded release JAR is 571,261 bytes with SHA-256 `ac04ffcce1d49a53fdd2cc66965be0db5d470f8399d62ec8290fda9e04148b7d`; it matches the published `SHA256SUMS.txt` and passes `scripts/verify_jar.py`. No third-party pack visual check, physical-GPU result, clean teardown or full Iris parity is claimed.

## Beta.9 published prerelease — 2026-10-20

PR #16 merged to `main` as `3ac3f16b25fe16c9c5c78967f30c9cc1650f8c51`; tag `v0.2.0-beta.9+mc1.21.11` and the GitHub prerelease were published by Release workflow `39344144615`. Its downloaded JAR is 565,211 bytes, SHA-256 `9c773e71622e21f0c5af3328849e84a1de02cf720ad62ceaadeeb45edfd73dcd`, matching `SHA256SUMS.txt` and passing `scripts/verify_jar.py`.

The terrain `viewPos` Lavapipe readback measured 222,792 active magenta pixels / 0 disabled, with zero runtime Vulkan validation errors. The run recorded 10,031 `VUID-vkDestroyDevice-device-05137` shutdown diagnostics, matching the same-run vanilla baseline; this is not a clean-teardown claim. Third-party packs were not visually checked.

## Beta.7 published prerelease — 2026-10-20

PR #9 merged as `da44c16aaf5188e369a3902affbf46beefb896c4`. The [Release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39331562077) passed pinned source audit, all 13 Lavapipe graphics fixtures, build/tests, distributable verification and publication. The [beta.7 prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.7%2Bmc1.21.11) includes `veyra-0.2.0-beta.7+mc1.21.11.jar` (565,114 bytes), SHA-256 `da1d89e77a582f6b5332bda72cf0d4fbe5d3b60da18003d190ac0f5a5485144b`. The downloaded JAR passed `scripts/verify_jar.py`; its hash matches the published `SHA256SUMS.txt`.

The pinned source audit covers 75 default/profile/dimension cases and 5,178 sources; `visualValidation=false` for every entry. All 13 Lavapipe fixtures pass. The cloud fixture reported 52,412 active magenta pixels / 0 with shaders disabled and 0 runtime Vulkan validation errors. Locally, JDK 21.0.12.1 ran `test validateShaders build packageExamplePacks` successfully: **187 JUnit tests, zero failures/errors/skips**, 40 example shaders processed and 37 compiled directly to Vulkan SPIR-V. Synthetic checks do not verify third-party pack visuals on a physical GPU or full Iris parity.

## PR #11 terrain varying aliases — 2026-10-20

PR #11 adds the Iris terrain `lmCoord` alias for VulkanMod's existing location-5 lightmap input and the BSL `color` alias for its location-0 vertex color input. Both aliases have adapter regression tests. `examples/geometry-lightmap` now includes a terrain fragment that uses both aliases and samples the lightmap; the example compiles to Vulkan SPIR-V and is included in the example-pack archive.

On PR head `cc56216`, Build [`39334381472`](https://github.com/glebKovshov/Veyra/actions/runs/39334381472), pinned source audit [`39334381384`](https://github.com/glebKovshov/Veyra/actions/runs/39334381384) and all 13 Lavapipe graphics fixtures [`39334381363`](https://github.com/glebKovshov/Veyra/actions/runs/39334381363) passed. Local JDK 21 `test validateShaders build packageExamplePacks` passed: **190 tests, zero failures/errors/skips**, 41 example shaders processed and 38 compiled directly to Vulkan SPIR-V; the distributable JAR passed `scripts/verify_jar.py`.

The audit still covers 75 cases / 5,178 sources and reports 72 accepted / 645 rejected, with `visualValidation=false` for every case. The aliases cleared the first terrain blockers but did not increase whole-program acceptance: Complementary terrain/water now fail on `signMidCoordPos` (`mc_midTexCoord` is absent from the terrain vertex data); BSL terrain fails on `viewPos`, and BSL water still requires `mat`. The Lavapipe checks exercise existing synthetic scenes, not these third-party shaders. No real-pack visual check, physical GPU result or full Iris parity is claimed.

## PR #12 terrain adapter diagnostics — 2026-10-20

The terrain adapter now reports the exact unsupported input (`mc_Entity`, `mc_midTexCoord`, `midTexCoord`, `viewPos`, `viewNormal`, `tangent`, or `at_tangent`) instead of grouping unrelated inputs into one message. Regression tests also confirm that legacy `gl_FragData` output rewriting remains supported. A fresh pinned audit used the same 4 pack archives, covering 12 defaults and 63 declared profile/dimension cases; it still reports 72 accepted and 645 rejected, with every record marked `visualValidation=false`. Default Overworld terrain errors now identify `signMidCoordPos` for both Complementary packs and `viewPos` for BSL.

The inspected VulkanMod 0.6.8 `TerrainBufferBuilder.vertex(...)` accepts position, packed color, UV0, and two lightmap values. It does not receive a midpoint-UV attribute. Correct `mc_midTexCoord` support needs a terrain vertex data/format change that can compute and carry each quad's midpoint; no fragment-level alias was added. Local JDK 21.0.12.1 `test validateShaders build packageExamplePacks --rerun-tasks` passed with 192 tests, 41 example shaders processed and 38 direct SPIR-V compilations. The beta.8 candidate JAR passed `scripts/verify_jar.py` (565,160 bytes; SHA-256 `26dc7ca90bc5f2db5190379747b5c3c7c27264d7caf9700da04111dfaaf041e2`). On final PR head `6b214b1`, Build [`39337704037`](https://github.com/glebKovshov/Veyra/actions/runs/39337704037), pinned source audit [`39337703955`](https://github.com/glebKovshov/Veyra/actions/runs/39337703955) and all 13 Lavapipe fixtures in Graphics smoke [`39337703956`](https://github.com/glebKovshov/Veyra/actions/runs/39337703956) passed. PR #12 merged as `738b27621b9d66e348795c8781272d35db89949f`; beta.8 was later published successfully by [Release workflow `39338631777`](https://github.com/glebKovshov/Veyra/actions/runs/39338631777), with the downloaded asset hash verified above.

## 0.2.0-beta.3 candidate — local checks — 2026-10-19

On `feature/cloud-fog-uniforms`, `test --rerun-tasks validateShaders build packageExamplePacks` passed on Temurin JDK 21.0.12.1: **161 JUnit tests, 0 failures/errors/skips**; shaderc preprocessed 39 example shaders and compiled 36 to Vulkan SPIR-V, including the new cloud fog fixture. The pinned source audit passed SHA-512 checks for all four source archives and covered 75 default/profile/dimension cases / 5,178 sources; 18 fragments were accepted and 699 rejected. `fogColor` no longer appears as a cloud adapter error; other pack blockers remain. `visualValidation` is false in every audit entry.

The beta.3 candidate JAR was inspected against the release verifier's content checks: 510,745 bytes, SHA-256 `06647cc838563ca7b562b9b522741bbbabe78fba820ba15a2978407ae6675be3`, Minecraft 1.21.11 / VulkanMod 0.6.8. This was a local PowerShell equivalent of `scripts/verify_jar.py`; the Windows workspace has no Python interpreter. GitHub PR Build/source-audit/graphics checks and release publication are pending. No third-party pack or physical GPU was visually tested.

## 0.2.0-beta.2 release — 2026-10-19

PR #3 merged as `4be1bbaf7324b80b8aab09368e8c9e2b908e2eda`; lightweight tag `v0.2.0-beta.2+mc1.21.11` points to that commit. The [Release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39289684005) completed source audit, all 12 Lavapipe fixtures, build/tests, distributable verification and publication. GitHub lists the [published prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.2%2Bmc1.21.11).

A forced local JUnit run passed **160 tests** with zero failures/errors. On JDK 21, `clean test validateShaders build packageExamplePacks` passed; shaderc preprocessed 39 example shaders and compiled 36 directly to Vulkan SPIR-V, plus four conditional-include checks, entity shadow-sampler checks and the unbound-output dataflow compile. The pinned source audit covered 75 default/profile/dimension cases and 5,178 sources; adapters accepted 18 and rejected 699 geometry fragment combinations. The source-only audit clears BSL's `shadowFade` stage but still reports per-draw entity IDs, cloud texture and a source-only `BIOME_DESERT` registry lookup.

The first graphics run exposed the missing terrain `shadowFade` provider. A follow-up exact-commit smoke exposed a duplicate post UBO member when the pack overrides that built-in; both defects are fixed. The final [PR Build](https://github.com/glebKovshov/Veyra/actions/runs/39288725708), [source audit](https://github.com/glebKovshov/Veyra/actions/runs/39288725787) and [12-fixture graphics matrix](https://github.com/glebKovshov/Veyra/actions/runs/39288725971) pass on the release tree.

The release JAR was downloaded and passed `scripts/verify_jar.py` for Minecraft 1.21.11 and VulkanMod 0.6.8. Its SHA-256 `4238a919c89e40aa7435fc8a9129748f39a4a89785848dace5fdf1ce20199f73` matches the published `SHA256SUMS.txt`. The release is a beta prerelease; source and synthetic graphics checks do not establish full Iris parity or real-pack visuals.

The first push/PR graphics-smoke runs on `c4e74c8` exercised the extended custom-uniform fixture and exposed a terrain-provider failure: `Unknown shader pack float uniform shadowFade`. The post provider already resolved the override, but the terrain provider did not. A shared resolver now applies the frame fallback or pack override in both paths and rejects non-float overrides. The post UBO also merges built-in and pack-defined names without duplicate fields.

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


### GitHub CI for beta.3 candidate

PR #4 at commit `566e059c433bd64a14184a5d0e6dd66d394bff7d` passed the GitHub Build workflow ([39294368264](https://github.com/glebKovshov/Veyra/actions/runs/39294368264)), all 12 Lavapipe graphics smoke fixtures ([39294368250](https://github.com/glebKovshov/Veyra/actions/runs/39294368250)), and the pinned shaderpack source audit ([39294367864](https://github.com/glebKovshov/Veyra/actions/runs/39294367864)). Real shaderpack visual validation remains outstanding.


### Published beta.3 artifact

The merged beta.3 candidate passed the final PR Build, shaderpack source audit, and all 12 Lavapipe graphics smoke fixtures. The tag release workflow also passed its source/graphics gates, `./gradlew build validateShaders packageExamplePacks`, and `scripts/verify_jar.py` (release run [39295725182](https://github.com/glebKovshov/Veyra/actions/runs/39295725182)). Downloaded [veyra-0.2.0-beta.3+mc1.21.11.jar](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.3%2Bmc1.21.11/veyra-0.2.0-beta.3%2Bmc1.21.11.jar) is 510,727 bytes and has SHA-256 `5a397886d9781d1bdf10a22484a97f320614147abbbe57a3833d0e1a00b62552`, matching the published `SHA256SUMS.txt`.

These graphics checks use software Vulkan/Lavapipe and synthetic fixtures. They do not establish real shaderpack visual compatibility on a physical GPU; pinned pack-source results continue to mark every case `visualValidation=false`.

### Beta.4 candidate local verification — 2026-10-19

On `feature/iris-entity-identifiers`, `test --rerun-tasks validateShaders build packageExamplePacks` passed with JDK 21. JUnit reports 166 tests, 0 failures, 0 errors, and 0 skips. Shader validation processed 39 example shaders, compiled 36 directly to Vulkan SPIR-V, and passed the entity-ID, entity-shadow, cloud-fog, four conditional-include, and unbound-output dataflow checks.

The local candidate `veyra-0.2.0-beta.4+mc1.21.11.jar` is 527,755 bytes, SHA-256 `deb6de3b242bd8ae71536c6b778fc3e9a0c731f22ba73a94f39b18ab74bde436`. A ZIP/JAR inspection confirmed `fabric.mod.json`, the entity/block ID-map classes, `EntityRenderDispatcherMixin`, nested `jcpp-1.4.14.jar` with its preprocessor class, and no shader-smoke harness classes. This local hash is for the Windows build and is not the future published artifact checksum; the release workflow will run the official `scripts/verify_jar.py` against the Linux release artifact.

### Beta.4 PR verification and merge — 2026-10-19

PR #5 ([Add Iris entity and block entity identifiers](https://github.com/glebKovshov/Veyra/pull/5)) passed its Build run [39300268861](https://github.com/glebKovshov/Veyra/actions/runs/39300268861), pinned source audit [39300268808](https://github.com/glebKovshov/Veyra/actions/runs/39300268808), and all 12 Lavapipe graphics fixtures [39300269051](https://github.com/glebKovshov/Veyra/actions/runs/39300269051). The audit reports 12 default and 63 declared-profile/dimension cases, with 18 geometry fragment programs accepted and 699 rejected. The PR merged to `main` as `f3ca6b0bae84efbde979b46b16767e7d175371c6`. These checks validate Veyra's supported source subset and synthetic render paths; no third-party pack was visually validated.

### Published beta.4 artifact

Tag `v0.2.0-beta.4+mc1.21.11` points to `2fce9f7ed764f9cd62ba7feaa0d931e9b3b6ae07`. The final [release workflow](https://github.com/glebKovshov/Veyra/actions/runs/39301135037) passed its source audit, all 12 graphics fixtures, `./gradlew build validateShaders packageExamplePacks`, and `scripts/verify_jar.py`; the tag Build [39301135146](https://github.com/glebKovshov/Veyra/actions/runs/39301135146) also passed.

The [beta.4 GitHub prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.4%2Bmc1.21.11) includes the installable [veyra-0.2.0-beta.4+mc1.21.11.jar](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.4%2Bmc1.21.11/veyra-0.2.0-beta.4%2Bmc1.21.11.jar), source JAR, example-packs ZIP, and [SHA256SUMS.txt](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.4%2Bmc1.21.11/SHA256SUMS.txt). The installable JAR was downloaded again: 527,737 bytes, SHA-256 `dd2230e13f64c1797477ab689dc3fdd44e4da9e82c9ab5abdadf7c283cc97a03`, matching the published checksum manifest. ZIP inspection confirmed the entity/block ID-map classes and dispatcher mixin are present and the smoke harness is absent.

The prerelease remains experimental. Lavapipe covers synthetic fixtures only; no third-party pack was visually validated on a physical GPU.

### Published beta.5 artifact

Tag `v0.2.0-beta.5+mc1.21.11` points to release commit `7e5fd09c42338cf5802e63b9519d3548b8456230`. Release workflow [39306047963](https://github.com/glebKovshov/Veyra/actions/runs/39306047963) passed its pinned source audit, all 12 Lavapipe fixtures, Gradle build/tests, and JAR verifier. The published [beta.5 GitHub prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.5%2Bmc1.21.11) contains the installable JAR, sources, example packs, and checksums. Its installable JAR is 531,535 bytes with SHA-256 `badc8f9c49449abab1a1a9d8bf6b02937e19196f86466696bbbbcce61661cee7`, matching the published checksum manifest.

### Published beta.6 artifact — 2026-10-20

PR #8 merged to `main` at `871cb79d1618d8eb9b3de5681edcc7f58a172965`. Its Build run [39319780312](https://github.com/glebKovshov/Veyra/actions/runs/39319780312) and beta.6 Release run [39319780416](https://github.com/glebKovshov/Veyra/actions/runs/39319780416) passed. The release run includes the pinned source audit, all 12 Lavapipe graphics fixtures, Gradle build/tests, and the JAR verifier.

Tag `v0.2.0-beta.6+mc1.21.11` points to that commit. The published [beta.6 prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.6%2Bmc1.21.11) has an installable JAR of 555,025 bytes with SHA-256 `e1146170f016fb5d37b919fdde638587da7cf880bc44edbccb3bcead82b9a06d`, matching its `SHA256SUMS.txt`.

### Beta.7 local candidate — 2026-10-20

The working branch continues from the published beta.6 commit. On JDK 21, `test validateShaders build packageExamplePacks` passed after the cloud scene-color snapshot, active `noisetex` support, and legacy `texture2DLod` lowering were in place: **185 JUnit tests, 0 failures/errors/skips**. Shader validation processed 40 example sources and compiled 37 directly to Vulkan SPIR-V. The installable beta.7 candidate passes `scripts/verify_jar.py`; the local JAR is 564,498 bytes with SHA-256 `2f7c4b6dc04704253cf3b10522727d4335b1adfde34c10764fdc0c8536d075cc`.

The refreshed pinned source audit covers 12 default and 63 declared-profile/dimension cases. All 5,178 stages preprocess; geometry adapters accept 72 and reject 645 fragments. Complementary cloud programs' observed `colortex0` and `noisetex` LOD sampling now pass the source adapter. BSL cloud sampling and Sildur cloud inputs remain unsupported. Every record still has `visualValidation=false`, so no complete third-party pack is certified. The default and profile JSON in `validation/` were regenerated from the same report.

The `cloud-colortex0` graphics test is a synthetic fixture. PR checks, all 13 Lavapipe fixtures, merge, and beta.7 publication are pending. Neither the source-only audit nor synthetic fixtures establish complete pack compatibility or physical-GPU appearance.
