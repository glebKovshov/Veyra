# Veyra 0.2 validation status


## Shadow entity-stage linking follow-up - 2026-10-22

The entity shadow pipeline now composes Minecraft's native vertex stage with the selected pack `shadow.vsh` and links its varyings to the matching fragment stage. The extended `shadow-color-entity` fixture passes on an NVIDIA RTX 3070: the actual entity shadow pipeline consumed `flat mat` from the custom vertex stage and produced **18,764 active readback pixels**. The JDK 21 full local run passed **392 tests**, preprocessed **96** example shaders, directly compiled **112** to Vulkan SPIR-V, built the distributables, and passed `scripts/verify_jar.py`. Its unpublished beta.30-metadata JAR is **849,405 bytes**, SHA-256 `1aa80289549b5511af4558d8be8be4eaac4d74d1083dac8a78b993d937ab6a49`.

The refreshed four-pack audit in `validation/` contains 12 defaults and 63 profile cases across 5,178 declared stages (5,124 preprocessed). It accepts **594** and rejects **270** geometry fragments; **18/18** active compute stages compile. The 54 Complementary rows still report missing entity/hand `mc_midTexCoord` inputs and unsupported cloud `renderStage`; only 21/75 rows have no capability or adapter error. Every row remains `visualValidation=false`. The RTX `COMPLEMENTARY` profile run stops on `gbuffers_entities.vsh`'s active `mc_midTexCoord`, before visual validation. The synthetic fixture fixes and verifies the shadow vertex-to-fragment varying contract but does not certify a real Complementary profile. PR #84 remains draft and the release goal remains incomplete.

## Weather vertex execution candidate — source validation passed

On `feature/weather-vertex-execution`, the pinned source/profile/dimension audit passed for 4 SHA-512-pinned packs, 12 defaults, 63 declared-profile cases, and 5,178 stages: **864 geometry fragments accepted / 0 rejected / 0 adapter errors**. No audited pack reports a `gbuffers_weather.vsh` capability error. The 21 remaining whole-pack capability errors are 18 selected Complementary `shadowcomp.csh` stages and 3 Sildur `mc_Entity` inputs. All 75 audit rows retain `visualValidation=false`.

Local JDK 21 `test build validateShaders compileGraphicsSmokeJava packageExamplePacks --rerun-tasks --no-daemon` passed **336 tests** with 0 failures/errors/skips, processed 86 examples, compiled 100 stages directly to Vulkan SPIR-V, and completed `build`, graphics-smoke Java compilation, and example-pack packaging. `scripts/verify_jar.py` passed on the local beta.30-metadata feature artifact (755,156 bytes; SHA-256 `9ee88e134be7ef179f11c64e1a239e389118574f47833575f5a565267392373f`). The rainy-world runtime fixture is added to the 35-case Lavapipe matrix; its real graphics result, PR, merge, and next batched release are pending.

## Beta.30 experimental prerelease — published and verified

Release-prep PR [#81](https://github.com/glebKovshov/Veyra/pull/81) merged into `main` at `74b0a1940edc954ec0edbf4a00238bd80994e60f`. Tag `v0.2.0-beta.30+mc1.21.11` and the [GitHub prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.30%2Bmc1.21.11) were published successfully by Release workflow [39568060859](https://github.com/glebKovshov/Veyra/actions/runs/39568060859), which passed source audit, all 34 Lavapipe fixtures, build/tests, and distributable verification.

The published install [JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.30%2Bmc1.21.11/veyra-0.2.0-beta.30%2Bmc1.21.11.jar) is **753,755 bytes**, SHA-256 `96edff62b629e4882e3a635823d61e08e59184301646eb0339dc60e4bbaef1d0`. It includes the CloudRenderer selector remap that fixes the beta.25 crash. The published beta.25 and beta.29 assets remain unchanged.

## Beta.29 experimental prerelease — published and verified

Feature PR [#77](https://github.com/glebKovshov/Veyra/pull/77) added the batch; release-prep PR [#78](https://github.com/glebKovshov/Veyra/pull/78) merged as `7a11ea2318019b0cff13a060fe5c163d8ea931d8`. Annotated tag `v0.2.0-beta.29+mc1.21.11` points to that commit. Tag Build [39553458503](https://github.com/glebKovshov/Veyra/actions/runs/39553458503) and Release workflow [39553458506](https://github.com/glebKovshov/Veyra/actions/runs/39553458506) passed. The release workflow passed the pinned source audit, **34/34** Lavapipe graphics fixtures, versioned build/tests, distributable verifier, and publication.

The published [GitHub prerelease Veyra beta.29](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.29%2Bmc1.21.11) includes install [JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.29%2Bmc1.21.11/veyra-0.2.0-beta.29%2Bmc1.21.11.jar): **749,470 bytes**, SHA-256 `b532fbdf6e86fc7ac0541c40bf8e16fe2719517364788b9eeb80e6c9cffa01eb`; sources JAR **272,535 bytes**, SHA-256 `199d41a647d6d612d24e37fb90b870007ea07995de9aeaa9160879c4985ef5a3`; example-packs ZIP **87,828 bytes**, SHA-256 `52bc70a9a10da44b9b10559a161add48fd1820917f797391ab8866a30dbbf925`. All three downloaded assets match the published [SHA256SUMS.txt](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.29%2Bmc1.21.11/SHA256SUMS.txt), and the install JAR passes `scripts/verify_jar.py` for Minecraft 1.21.11 / VulkanMod 0.6.8 / beta.29.

Local JDK 21 `test build validateShaders compileGraphicsSmokeJava packageExamplePacks --no-daemon` passed **330 tests**, preprocessed 85 shader examples, and compiled 98 stages directly to Vulkan SPIR-V. On an NVIDIA RTX 3070, the sky-stage fixture measured **14,249 active SUN pixels / 10,517 active MOON pixels / 0 after disabling Veyra**. These are synthetic path checks, not third-party shaderpack visual validation.

The pinned source audit covers 4 SHA-512-pinned archives / 12 defaults / 63 declared-profile-dimension cases / 5,178 stages; it accepts **781 geometry fragment programs and rejects 8**. All 75 audit rows retain `visualValidation=false`. First remaining blockers include the unavailable Minecraft lightmap sampler for `gbuffers_textured` (72 records), Sildur's unsupported `normal` output type (3), and Sildur `shadowData` in Nether/End (8 fragment rejections). No complete third-party shaderpack has been visually validated. The beta.25 CloudRenderer Mixin crash was fixed in beta.26 and is present in beta.28 and this candidate.

The local JDK 21 command `test build validateShaders compileGraphicsSmokeJava packageExamplePacks --no-daemon` passed **330 tests**, processed 85 examples, and compiled 98 stages directly to Vulkan SPIR-V. The Windows candidate JAR (749,499 bytes, SHA-256 `9c7132519c99ec96225cebee16f4d5f63b55ebf789c79d94ccd45f7234b15c11`) differs from the published Linux-built JAR only in LF versus CRLF line endings in `LICENSE_veyra`, `THIRD_PARTY_NOTICES.md`, and `licenses/jcpp-Apache-2.0.txt`; the 269 entry names match. Both pass distributable verification.

## Beta.28 experimental prerelease - published and verified

Release-prep PR [#75](https://github.com/glebKovshov/Veyra/pull/75) merged at `57f36f0868e4fd76332be6b3be53950f3cde3544`; tag `v0.2.0-beta.28+mc1.21.11` points to that commit. Release workflow [39543950206](https://github.com/glebKovshov/Veyra/actions/runs/39543950206) passed the pinned source audit, **30/30** Lavapipe graphics fixtures, versioned build and tests, distributable verifier, and publication.

The published [GitHub prerelease beta.28](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.28%2Bmc1.21.11) contains install [JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.28%2Bmc1.21.11/veyra-0.2.0-beta.28+mc1.21.11.jar): **743,415 bytes**, SHA-256 `c5856489b97165646f5f1ad062bddd895cb964f325c47551c8ce687b5e3bf508`; sources JAR **269,732 bytes**, SHA-256 `a3d129c8d91bd217a5233c239291ed656f039fc05b4120a446d2f447720fbb2c`; and example-packs ZIP **86,450 bytes**, SHA-256 `8e2ac90f86a91a11dedf26e22389d1cdac0efcd20ea91f635399358bb2848dd9`. Each downloaded asset matches the published [SHA256SUMS.txt](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.28%2Bmc1.21.11/SHA256SUMS.txt); the install JAR passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and Veyra beta.28.

The local JDK 21 command `test validateShaders build compileGraphicsSmokeJava packageExamplePacks --rerun-tasks --no-daemon` passed **323 tests**, processed 82 shader examples, and compiled 86 stages directly to Vulkan SPIR-V. The Windows candidate was 743,444 bytes with SHA-256 `2832278d544107e41c212df56870d45eeef8140cda2b855a17109ab6b6fde9f3`. It is 29 bytes larger than the official Linux-built JAR; all ZIP entries are present and match except CRLF versus LF line endings in the three text license/notice entries. All other uncompressed entry content is identical.

Synthetic NVIDIA RTX 3070 fixtures passed: entity vertex **18,532 active / 0 disabled** magenta pixels, sky-basic **226,295 / 0**, and `currentRenderedItemId` **13,984 / 0** green pixels. The pinned source audit covers 4 archives / 75 profile-dimension cases / 5,178 stages and accepts **781 / rejects 8** geometry fragments. First remaining blockers include `gbuffers_lightning.vsh` (54 cases), `gbuffers_skytextured.vsh` (13), `gbuffers_textured.vsh` (6), and Sildur `shadowData` (2); every audit row remains `visualValidation=false`. No complete third-party shaderpack has been visually validated; beta.28 remains experimental and the stable Iris-compatibility goal is incomplete.

## Beta.27 experimental prerelease - published (2026-10-21)

Feature PRs [#70](https://github.com/glebKovshov/Veyra/pull/70) and [#71](https://github.com/glebKovshov/Veyra/pull/71) merged damaged-block and sky-basic vertex execution. Release-prep PR [#72](https://github.com/glebKovshov/Veyra/pull/72) merged as `ce30d37cc8003b121d71bbbf1a09db458ab57573`; annotated tag `v0.2.0-beta.27+mc1.21.11` points to that commit. Release workflow [39535427317](https://github.com/glebKovshov/Veyra/actions/runs/39535427317) passed source audit, **33/33** Lavapipe graphics fixtures, versioned build/tests, distributable verification, and publication. PR #71 checks were Build [39534071885](https://github.com/glebKovshov/Veyra/actions/runs/39534071885), source audit [39534071977](https://github.com/glebKovshov/Veyra/actions/runs/39534071977), and Graphics smoke [39534071890](https://github.com/glebKovshov/Veyra/actions/runs/39534071890).

The [GitHub prerelease Veyra 0.2.0-beta.27+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.27%2Bmc1.21.11) includes install [JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.27%2Bmc1.21.11/veyra-0.2.0-beta.27+mc1.21.11.jar): **737,170 bytes**, SHA-256 `a7ec0eface6c55c16ab896fbf085e12d3f952065dab2f882edf285e7f5878fe1`. It matches the release [`SHA256SUMS.txt`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.27%2Bmc1.21.11/SHA256SUMS.txt) and passes `scripts/verify_jar.py`. The release also contains a 267,635-byte sources JAR and an 86,450-byte example-packs ZIP (SHA-256 `8e2ac90f86a91a11dedf26e22389d1cdac0efcd20ea91f635399358bb2848dd9`). Both distributable files were downloaded and checked against the release manifest.

The integrated local JDK 21 run passed **313 tests, 0 failures/errors/skips**, processed 82 example shader sources, and compiled 86 stages directly to Vulkan SPIR-V. The pinned audit covers 4 archives / 75 profile-dimension cases / 5,178 stages and accepts **781 / rejects 8** geometry fragments. Every row has `visualValidation=false`.

On NVIDIA GeForce RTX 3070, sky-basic loaded all three pipelines and measured **226,624 active magenta pixels, 0 when disabled**. This synthetic test does not validate complete third-party pack visuals. Remaining source blockers include `entityId` (54 cases), `renderStage` (12), `mc_Entity` (6), `shadowData` (2), and `gbuffers_skytextured.vsh` (1); selected Complementary profiles still declare unsupported `shadowcomp.csh` compute. The stable-release goal remains incomplete.
## Beta.26 experimental prerelease - published (2026-10-21)

Release-prep PR [#68](https://github.com/glebKovshov/Veyra/pull/68) merged as d9e58ea598555d24a02375f3e6fa2d56d1013bf3; tag v0.2.0-beta.26+mc1.21.11 points to that commit. Release workflow [39516955852](https://github.com/glebKovshov/Veyra/actions/runs/39516955852) passed the pinned source audit, **31/31** graphics smoke fixtures, release build/tests, distributable verification, and publication. Tag Build [39516955962](https://github.com/glebKovshov/Veyra/actions/runs/39516955962) also passed.

Downloaded published install JAR [veyra-0.2.0-beta.26+mc1.21.11.jar](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.26%2Bmc1.21.11/veyra-0.2.0-beta.26+mc1.21.11.jar): **731,597 bytes**, SHA-256 e9c80bf1d6914e275bb817a13859eb1b290b6feb0d1c1ae95254b021d5938be9. It matches the published [SHA256SUMS.txt](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.26%2Bmc1.21.11/SHA256SUMS.txt) and passes scripts/verify_jar.py for Minecraft 1.21.11 / VulkanMod 0.6.8 / Veyra beta.26. The release also includes the 265,175-byte sources JAR and 84,319-byte example-packs ZIP.

Forced local JDK 21 build validateShaders packageExamplePacks passed **299 tests, 0 failures/errors/skips**, processed 79 shader sources, and compiled 78 directly to Vulkan SPIR-V. The pinned audit covers 4 archives / 75 records / 5,178 stages and accepts **709 / rejects 8** geometry fragments. Every record retains visualValidation=false. The PR #67 physical-GPU entity, block-entity, and hand fixtures remain synthetic feature checks rather than full third-party pack visual validation.

Beta.26 contains the beta.25 cloud selector remap fix from PR #65. The beta.25 GitHub asset was not replaced. The user-provided 00:09:35 crash log predates installation of the patched local preview at 01:00:24; that preview was verified and the exact Loader 0.19.5 RTX 3070 cloud smoke passed separately. Beta.26 is experimental; full shaderpack visual validation and the stable-release goal remain outstanding.


## Beta.25 experimental prerelease - published (2026-10-20)

Feature PR [#62](https://github.com/glebKovshov/Veyra/pull/62) merged into `main` as `df3df827743adf9a978008c59fe9812cd16a699d`. On the final PR head, Build [39483079517](https://github.com/glebKovshov/Veyra/actions/runs/39483079517), pinned source audit [39483079473](https://github.com/glebKovshov/Veyra/actions/runs/39483079473), and Graphics smoke [39483079632](https://github.com/glebKovshov/Veyra/actions/runs/39483079632) all passed. The graphics matrix passed **25/25** Lavapipe fixtures. The glint fixture read back 313 bright green pixels while enabled and 0 after disabling the pack; CI detected no runtime Vulkan validation errors before shutdown. Process teardown still reports Vulkan diagnostics.

Release-prep PR [#63](https://github.com/glebKovshov/Veyra/pull/63) merged as `a2a391dc618a170b4ca47434dc35ee6f44516e3b`; annotated tag `v0.2.0-beta.25+mc1.21.11` points to that commit. Release workflow [39484980381](https://github.com/glebKovshov/Veyra/actions/runs/39484980381) passed the pinned source audit, **25/25** Lavapipe fixtures, versioned build/tests, distributable verification, and publication.

The [GitHub Release Veyra 0.2.0-beta.25+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.25%2Bmc1.21.11) is an experimental prerelease. Downloaded install [JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.25%2Bmc1.21.11/veyra-0.2.0-beta.25%2Bmc1.21.11.jar): **663,784 bytes**, SHA-256 `214fed2902216ea85d022cf638bc4f5b8dd17c79cba18ac702942c89ff8a5dd4`. It matches the published [`SHA256SUMS.txt`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.25%2Bmc1.21.11/SHA256SUMS.txt) and passes `scripts/verify_jar.py` for Minecraft 1.21.11 / VulkanMod 0.6.8 / beta.25. The example-packs ZIP is **76,776 bytes**, matches its entry in `SHA256SUMS.txt`, and includes `glint-vertex-execution`.

Forced local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed **263 tests, 0 failures/errors/skips**, preprocessed 58 example shaders, directly compiled 56 to Vulkan SPIR-V, packaged example packs, and compiled the graphics-smoke harness. Local candidate `veyra-0.2.0-beta.25+mc1.21.11.jar` is **663,802 bytes**, SHA-256 `91590cc5fea7d0ecdfca35415186b02ee753d93e447b8a9c753148cd98420e63`; it also passes `scripts/verify_jar.py`. The locally built candidate differs from the published GitHub artifact; the published JAR's checksum is verified against its release manifest above.

The pinned source audit covers 4 archives / 75 default-profile-dimension cases / 5,178 stages: **705 geometry fragments accepted / 12 rejected**. Complementary glint passes source adaptation; all 72 Complementary/BSL cases remain blocked by `gbuffers_basic.vsh`, 3 Sildur cases by glint attachment 4, and 12 Sildur fragment adapters by `LightC`. Every record retains `visualValidation=false`. Selected Complementary profiles still require unsupported `shadowcomp.csh`. No full third-party shaderpack visual validation or physical-GPU validation has been completed.

## Beta.24 experimental prerelease - published (2026-10-20)

Release-prep PR [#60](https://github.com/glebKovshov/Veyra/pull/60) merged as `d22b24a8271bf2771f96b923a32005f862d44134`; annotated tag `v0.2.0-beta.24+mc1.21.11` points to that commit. The [GitHub prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.24%2Bmc1.21.11) was published by Release workflow [39466425593](https://github.com/glebKovshov/Veyra/actions/runs/39466425593), which passed the source audit, **24/24** Lavapipe fixtures, release build/tests, distributable verification, and publication. Tag Build [39466425569](https://github.com/glebKovshov/Veyra/actions/runs/39466425569) also passed. The feature PR [#59](https://github.com/glebKovshov/Veyra/pull/59) had passed Build [39464726776](https://github.com/glebKovshov/Veyra/actions/runs/39464726776), pinned source audit [39464726807](https://github.com/glebKovshov/Veyra/actions/runs/39464726807), all 24/24 graphics fixtures [39464726902](https://github.com/glebKovshov/Veyra/actions/runs/39464726902), and post-merge Build [39465440876](https://github.com/glebKovshov/Veyra/actions/runs/39465440876).

Forced local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed **253 tests, 0 failures/errors/skips**, processed 55 example shaders, and compiled 52 directly to Vulkan SPIR-V. Local candidate `veyra-0.2.0-beta.24+mc1.21.11.jar` was 642,996 bytes, passed `scripts/verify_jar.py`, and had SHA-256 `3e77fead7149108bae1403e7752fc4c449c422be483873c96521bc248874c2f0`.

Downloaded published [`veyra-0.2.0-beta.24+mc1.21.11.jar`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.24%2Bmc1.21.11/veyra-0.2.0-beta.24%2Bmc1.21.11.jar): **642,978 bytes**, SHA-256 `edaaa23fe9b4bd5c4a6bfed210b12b407fc983260059c095db5eb32f3bbdd40e`. It matches the release [`SHA256SUMS.txt`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.24%2Bmc1.21.11/SHA256SUMS.txt) and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and Veyra 0.2.0-beta.24+mc1.21.11. The release also includes the sources JAR and example-packs ZIP.

The pinned source audit covers 4 archives / 75 default-profile-dimension cases / 5,178 stages: **705 geometry fragments accepted / 12 rejected**. All 75 whole-pack cases stop at the unsupported non-terrain `gbuffers_armor_glint.vsh`; all records have `visualValidation=false`. The 12 fragment rejections are Sildur `LightC` inputs. Selected Complementary profiles also require unsupported `shadowcomp.csh`. No full third-party shaderpack visual validation or physical-GPU validation has been completed. The terrain varying fixture measured 223,366 active pixels, 0 when shaders were disabled, and 0 runtime Vulkan validation errors; process teardown still reports 10,031 diagnostics.

## Unreleased armor-glint vertex candidate (2026-10-20)

On `feature/nonterrain-vertex-batch`, forced JDK 21 `test validateShaders compileGraphicsSmokeJava --rerun-tasks` and `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed: **263 tests, 0 failures/errors/skips**, 58 example shaders preprocessed, 56 directly compiled to Vulkan SPIR-V, and the graphics-smoke harness compiled. The CPU shaderc checks include custom armor-glint vertex/fragment stages and the Iris `tex` sampler alias.

The local beta.24-metadata candidate JAR is 663,335 bytes, SHA-256 `ecd885fce1ffe23ee2e4df3754ea47b10c9cbd5f297e95c455c17d8c63378f18`; it passes `scripts/verify_jar.py` for Minecraft 1.21.11 / VulkanMod 0.6.8. The example-packs ZIP contains `glint-vertex-execution`. The JAR has not been version-bumped and is not a release asset.

The fresh pinned source audit refreshed [`validation/shaderpack-source-audit.json`](validation/shaderpack-source-audit.json): 4 archives / 75 cases / 5,178 stages, **705 accepted / 12 rejected**, with `visualValidation=false` in every record. Complementary glint passes; `gbuffers_basic.vsh` remains the first blocker in 72 cases, Sildur attachment 4 blocks 3 cases, and 12 Sildur fragment cases require `LightC`. The Lavapipe glint fixture has been added to CI configuration but has not yet run on GitHub. No complete third-party shaderpack or physical-GPU visual validation is claimed.

## Beta.22 experimental prerelease - published (2026-10-20)

PR [#51](https://github.com/glebKovshov/Veyra/pull/51) merged geometry color inputs and feedback snapshots at `bc253f51ece852688b5b26b59b877effea7ff091`. Release-prep PR [#52](https://github.com/glebKovshov/Veyra/pull/52) merged at `3fe7b508f687e76bb00130ca3c42ad838f3f941c`; annotated tag `v0.2.0-beta.22+mc1.21.11` points to that commit. Release workflow [39440625486](https://github.com/glebKovshov/Veyra/actions/runs/39440625486) passed the pinned source audit, **22/22** Lavapipe fixtures, release build/tests, distributable verification, and publication. Tag Build [39440625450](https://github.com/glebKovshov/Veyra/actions/runs/39440625450) also passed.

Downloaded the published [`veyra-0.2.0-beta.22+mc1.21.11.jar`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.22%2Bmc1.21.11/veyra-0.2.0-beta.22%2Bmc1.21.11.jar): **617,133 bytes**, SHA-256 `3aba9ec38faea02d0be545a9a9e17cfbc8cdbd34a0570255a4811ad4cb77c480`. It matches the published [`SHA256SUMS.txt`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.22%2Bmc1.21.11/SHA256SUMS.txt) and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.22+mc1.21.11. The release also contains the sources JAR and example-packs ZIP.

Forced local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed on the merged commit: **243 tests, 0 failures/errors/skips**, 53 example shaders processed, and 50 compiled directly to Vulkan SPIR-V. The example-pack ZIP contains all five `entity-colortex-feedback` fixture files. The release audit covers 4 pinned packs / 75 default-profile-dimension records / 5,178 stages and accepts **705 / rejects 12** geometry fragments; every record has `visualValidation=false`.

The new entity feedback fixture read back **18,648 active magenta pixels**, 0 disabled pixels, and 0 runtime Vulkan validation errors on Lavapipe; teardown diagnostics remain. The source audit still rejects four Sildur translucent geometry programs per dimension that require pack-specific `LightC`; selected Complementary profiles still need unsupported `shadowcomp.csh` compute. BSL accepts 9/9 and Complementary accepts 10/10 default geometry fragments in each tested dimension. No complete third-party shaderpack visuals or physical-GPU results are claimed. Beta.22 is experimental and does not meet the stable-release goal.

## Beta.21 experimental prerelease - published (2026-10-20)

PR [#46](https://github.com/glebKovshov/Veyra/pull/46) merged the cloud vertex compatibility follow-up at `347c82086ba56716346575fa6962cd8d8398dc69`; release-prep PR [#47](https://github.com/glebKovshov/Veyra/pull/47) merged at `9d9821cc1fdfbbe6e5bd1baf3b5147b6c23605d6`. Annotated tag `v0.2.0-beta.21+mc1.21.11` points to the release commit. Release workflow [39427536453](https://github.com/glebKovshov/Veyra/actions/runs/39427536453) passed on attempt 2: pinned source audit, **21/21** Lavapipe graphics fixtures, release build, distributable checks, and publication. The first attempt hit a runner-only `apt-get update` 403 while installing Vulkan packages for `custom-uniforms`; that one job passed when rerun. Tag Build [39427536475](https://github.com/glebKovshov/Veyra/actions/runs/39427536475) also passed.

Downloaded release JAR [`veyra-0.2.0-beta.21+mc1.21.11.jar`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.21%2Bmc1.21.11/veyra-0.2.0-beta.21%2Bmc1.21.11.jar): **614,256 bytes**, SHA-256 `754588737164b4fbe9a5ac025bf1aeef4635bc6a9008c999c6f27fc93632cb1f`. It matches the release's [`SHA256SUMS.txt`](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.21%2Bmc1.21.11/SHA256SUMS.txt) and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.21+mc1.21.11. The [GitHub prerelease](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.21%2Bmc1.21.11) also includes the sources JAR and example-packs archive.

Forced local JDK 21 `build validateShaders packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed **241 tests, 0 failures/errors/skips**, processed 52 example shader sources, and compiled 49 directly to Vulkan SPIR-V. The pinned source audit covers 4 packs / 75 records / 5,178 stages and accepts **693 / rejects 24** geometry fragments; total source errors fell from 84 to 24. Every record has `visualValidation=false`. No complete third-party shaderpack visuals or physical-GPU results are claimed.

Known source limits include Complementary selected-profile `spriteBounds` and the unsupported `shadowcomp.csh` compute stage, plus Sildur `colortex4` and water attachment feedback. Vulkan teardown diagnostics and the broader stable-release goal remain unresolved; beta.21 is experimental.

## Beta.20 experimental prerelease — published (2026-10-20)

PR [#43](https://github.com/glebKovshov/Veyra/pull/43) merged the cloud-texture and vertex-input bridge at `abea76234afae81349b9b12be0aff794891284b6`; release metadata followed in PR [#44](https://github.com/glebKovshov/Veyra/pull/44), merged at `89ec7aa34ff8ead3fcd5afb2040b4908e7c493a1`. The annotated tag `v0.2.0-beta.20+mc1.21.11` points to the latter commit. Release workflow [39418635587](https://github.com/glebKovshov/Veyra/actions/runs/39418635587) passed its source audit, all **20/20** Lavapipe fixtures, release build/tests, distributable verification, and publication.

Downloaded release JAR `veyra-0.2.0-beta.20+mc1.21.11.jar`: **611,001 bytes**, SHA-256 `efb4494ed8083774611a0209297b72f22fd172977a6be1e8b080976e77a602b4`. It matches the published `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and Veyra `0.2.0-beta.20+mc1.21.11`. The source JAR and example-packs ZIP are also published.

On the published source commit, forced local JDK 21 `test validateShaders build packageExamplePacks compileGraphicsSmokeJava --rerun-tasks` passed **238 tests, 0 failures/errors/skips**, processed 51 example shaders, and compiled 48 directly to Vulkan SPIR-V. The final audit covers 4 pinned packs / 75 cases / 5,178 stages and accepts **690 / rejects 27** geometry fragment programs. Every audit record has `visualValidation=false`. The final cloud fixture read back 45,888 active pixels, 0 runtime Vulkan validation errors, and 10,031 teardown diagnostics. These checks cover source adaptation and synthetic graphics fixtures, not full third-party pack appearance.

Known limits remain: selected Complementary profiles need `spriteBounds` and unsupported `shadowcomp.csh` compute; its cloud vertex input `lmCoord` is unavailable. BSL defaults pass 9/9 geometry fragments per tested dimension, but the full pack has not been visually tested. Sildur accepts 0/5 default geometry fragments and still needs `colortex4`, attachment feedback, and cloud inputs. No third-party shaderpack visual or physical-GPU validation is claimed. Beta.20 is experimental; the stable-release goal remains incomplete.

## Beta.19 experimental prerelease — published 2026-10-20

Beta.19 is published from annotated tag [`v0.2.0-beta.19+mc1.21.11`](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.19%2Bmc1.21.11) at merge commit [`de65a42`](https://github.com/glebKovshov/Veyra/commit/de65a42886cc368a71ecd4e326ed7802633e34eb). Release workflow [39407213882](https://github.com/glebKovshov/Veyra/actions/runs/39407213882) passed source audit, all **19/19** Lavapipe fixtures, release build/tests, JAR verification, and publication. Tag Build [39407213769](https://github.com/glebKovshov/Veyra/actions/runs/39407213769) passed; its test report artifact [12257857082](https://github.com/glebKovshov/Veyra/actions/runs/39407213769/artifacts/12257857082) contains **234 tests, 0 failures/errors/skips**.

The pinned audit artifact [12257937543](https://github.com/glebKovshov/Veyra/actions/runs/39407213882/artifacts/12257937543) covers 75 cases / 5,178 stages and accepts **672 / rejects 45** geometry fragments; all records retain `visualValidation=false`. BSL accepts 8/9 defaults in each dimension after the new terrain/water bridges. The released main JAR is **593,532 bytes**, SHA-256 `78192452d957306c74e1cc3bad01e2220b01c9877b74586aff065f5a0c4b8f4c`; the downloaded asset matches published `SHA256SUMS.txt` and passes `scripts/verify_jar.py`. The water readback measured 226,902 active magenta / 0 disabled pixels and 0 runtime Vulkan validation errors; teardown emitted 10,049 diagnostics. Third-party pack visuals and physical-GPU behavior remain unverified.

## Beta.18 experimental prerelease: legacy fog bridge — 2026-10-20

Beta.18 is published from annotated tag [`v0.2.0-beta.18+mc1.21.11`](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.18%2Bmc1.21.11) at merge commit [`e3428eb`](https://github.com/glebKovshov/Veyra/commit/e3428eb782a0d715b1b1bd22e167c34c48b9bf94). Release workflow [39400531356](https://github.com/glebKovshov/Veyra/actions/runs/39400531356) and Tag Build [39400531360](https://github.com/glebKovshov/Veyra/actions/runs/39400531360) passed. The source-audit artifact [12253973732](https://github.com/glebKovshov/Veyra/actions/runs/39400531356/artifacts/12253973732) records 75 cases / 5,178 stages and **636 accepted / 81 rejected** geometry fragments; all records have `visualValidation=false`. Release graphics passed all **19/19 Lavapipe fixtures**.

PR [#37](https://github.com/glebKovshov/Veyra/pull/37) adapts legacy `gl_Fog.color`, `.start`, `.end`, and derived `.scale` to the Minecraft Fog UBO; `.density` remains explicitly unsupported. Local forced JDK 21 `build validateShaders packageExamplePacks --rerun-tasks` passed **231 tests, 0 failures/errors/skips**, processed 50 examples, and compiled 47 directly to Vulkan SPIR-V. The published `veyra-0.2.0-beta.18+mc1.21.11.jar` is **593,110 bytes**, SHA-256 `c0b90dc3d00d04c756f57ec7652b12beca4a6f34d3f9fb1aa11a179525ab0323`; it matches release `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and the beta.18 mod version. The tracked audit JSONs match the 636 / 81 results, replacing stale beta.16 counts (486 / 231).

Complementary Reimagined and Unbound r5.9.3 now accept 10/10 default geometry fragments in all three dimensions. Selected profiles still fail on `spriteBounds` or the unsupported `shadowcomp.csh` compute stage. BSL remains 6/9 with `recolor`, water `dist`, and cloud `texture` blockers; Sildur's Vibrant remains 0/5 due to `colortex4`, attachment feedback, and cloud inputs. No physical-GPU or third-party-pack visual validation is available; Lavapipe teardown still reports validation diagnostics.

## Beta.17 experimental prerelease — 2026-10-20

Published [Veyra 0.2.0-beta.17+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.17%2Bmc1.21.11) from annotated tag `v0.2.0-beta.17+mc1.21.11` at `ab14d1ad43c1094fdd19988413eda296c4ed3f8f`. Release workflow [39395224331](https://github.com/glebKovshov/Veyra/actions/runs/39395224331) passed the pinned source audit, all 19 Lavapipe fixtures, release build/tests, JAR verification, and publication. Tag Build [39395224342](https://github.com/glebKovshov/Veyra/actions/runs/39395224342) also passed `build validateShaders` and distributable verification.

The release source-audit artifact [12251935741](https://github.com/glebKovshov/Veyra/actions/runs/39395224331/artifacts/12251935741) covers 75 default/profile/dimension cases, 4 pinned packs, and 5,178 stages: 528 geometry fragments accepted / 189 rejected. Every case has `visualValidation=false`. All 19 graphics fixtures passed; the terrain-midpoint fixture read back 63,883 active magenta pixels / 158,771 opposite-region pixels / 0 disabled magenta pixels and 0 runtime Vulkan validation errors. Shutdown emitted 10,031 descriptor-pool diagnostics. These synthetic fixtures do not validate third-party pack visuals.

Downloaded release asset `veyra-0.2.0-beta.17+mc1.21.11.jar`: **591,342 bytes**, SHA-256 `18964b22255ad2ce78d570845e9a1ca48c8fc87ca1ef80bea363d58c29109214`. It matches the published `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.17+mc1.21.11. The release also includes the sources JAR and example-packs ZIP.

The versioned local beta.17 candidate passed forced JDK 21 `build validateShaders packageExamplePacks --rerun-tasks`: 229 tests, 0 failures/errors/skips; 50 shader examples and 47 direct Vulkan SPIR-V compilations. No physical-GPU or third-party shaderpack visual result is claimed. Beta.17 remains experimental; clean Vulkan teardown and the stable-release goal remain unresolved.

## Beta.16 experimental prerelease — 2026-10-20

Published [Veyra 0.2.0-beta.16+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.16%2Bmc1.21.11) from annotated tag `v0.2.0-beta.16+mc1.21.11` at commit `abf0d7106db88714fea94356b3605e9c129ef036`. Release workflow [39387855512](https://github.com/glebKovshov/Veyra/actions/runs/39387855512) passed source audit, all graphics fixtures, build, JAR verification, and publication.

Tag Build run [39387855462](https://github.com/glebKovshov/Veyra/actions/runs/39387855462) passed **227 tests, 0 failures, 0 errors, and 0 skipped**, `validateShaders`, build, and JAR verification. Shaderc processed 50 example shaders and compiled 47 directly to Vulkan SPIR-V. Post-merge Build [39387804543](https://github.com/glebKovshov/Veyra/actions/runs/39387804543) also passed.

All 19 Lavapipe fixtures passed. The terrain-midpoint fixture read back 60,049 active magenta pixels / 162,877 opposite-region pixels / 0 disabled magenta pixels, with 0 runtime validation errors. Process teardown still reported 10,031 diagnostics. These are synthetic graphics fixtures, not third-party shaderpack visuals.

Release source-audit artifact [12248416739](https://github.com/glebKovshov/Veyra/actions/runs/39387855512/artifacts/12248416739) covers 75 cases, 4 pinned packs, and 5,178 stages: 486 geometry fragments accepted / 231 rejected. The downloaded artifact matches the tracked audit JSON; all records have `visualValidation=false`.

Downloaded release asset `veyra-0.2.0-beta.16+mc1.21.11.jar`: **590,888 bytes**, SHA-256 `3574e4dc2bcfd051b7972ed2ed6e6c99359f2bc9a143ae51972bfd2718d1f487`. It matches `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.16+mc1.21.11. The sources JAR and example-packs ZIP are also published.

No physical-GPU or third-party shaderpack visual result is claimed. Beta.16 is experimental; clean Vulkan teardown and the stable-release goal remain unresolved.

## Beta.15 experimental prerelease — 2026-10-20

Published [Veyra 0.2.0-beta.15+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.15%2Bmc1.21.11) from annotated tag `v0.2.0-beta.15+mc1.21.11` at commit `b3b6d0e0d46efb62a22e3769509f785e6961481e`. Release workflow [39382531868](https://github.com/glebKovshov/Veyra/actions/runs/39382531868) passed all source, graphics, build, and publication jobs.

Tag Build run [39382531862](https://github.com/glebKovshov/Veyra/actions/runs/39382531862) passed with **226 tests, 0 failures, 0 errors, and 0 skipped**, `validateShaders`, build, and JAR verification. The source-audit artifact [12246036591](https://github.com/glebKovshov/Veyra/actions/runs/39382531868/artifacts/12246036591) covers 75 cases, 4 pinned packs, and 5,178 stages: 432 geometry fragments accepted / 285 rejected. All records have `visualValidation=false`.

All 19 Lavapipe fixtures passed. Terrain-ID readback was 223,462 active magenta pixels / 0 disabled; water-ID was 226,902 / 0. Both had 0 runtime validation errors before shutdown; teardown still emitted 10,031 and 10,050 validation diagnostics respectively. These are synthetic fixtures, not real shaderpack visuals.

Downloaded release asset `veyra-0.2.0-beta.15+mc1.21.11.jar`: **590,815 bytes**, SHA-256 `179a299d6b2c3d436e675fb022f4618a7dee74318deb07a2e36315eb9a49341d`. It matches `SHA256SUMS.txt` and passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.15+mc1.21.11. The sources JAR and example-packs ZIP are also published.

No physical-GPU or third-party shaderpack visual result is claimed. Beta.15 is experimental; clean Vulkan teardown and the stable-release goal remain unresolved.

## Beta.14 experimental prerelease — 2026-10-20

Published [Veyra 0.2.0-beta.14+mc1.21.11](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.14%2Bmc1.21.11) from annotated tag `v0.2.0-beta.14+mc1.21.11` at commit `8aae40b333f47ebfef2929bb5100f7a689ee1f9f`. Release workflow [39375080267](https://github.com/glebKovshov/Veyra/actions/runs/39375080267) passed its pinned source audit, all 17 Lavapipe fixtures, `build validateShaders packageExamplePacks`, distributable verification, and publication.

The tag Build run [39375080222](https://github.com/glebKovshov/Veyra/actions/runs/39375080222) passed with **220 tests, 0 failures, 0 errors, and 0 skipped**. It processed 48 example shaders and compiled 45 directly to Vulkan SPIR-V. Its final-version JAR passes `scripts/verify_jar.py` for Minecraft 1.21.11, VulkanMod 0.6.8, and mod 0.2.0-beta.14+mc1.21.11.

The release audit artifact [12242444433](https://github.com/glebKovshov/Veyra/actions/runs/39375080267/artifacts/12242444433) contains 75 records across 4 pinned packs / 5,178 stages, with 486 geometry fragments accepted and 231 rejected. All records have `visualValidation=false`. The packs are not visually validated in-game.

All 17 Lavapipe fixtures passed. Entity MRT read back 18,532 active magenta pixels through `colortex1`; `specular-entity` read back 18,293. Runtime Vulkan validation errors were 0. Process teardown still emitted 10,035 validation diagnostics.

Downloaded release asset `veyra-0.2.0-beta.14+mc1.21.11.jar`: **583,317 bytes**, SHA-256 `4d21b657604fb853d531f335c0f9cdd159c93e5f300cf9069ec4e98dab0c3650`. It matches `SHA256SUMS.txt` and passes `scripts/verify_jar.py`. The sources JAR and example-packs ZIP are also published.

No physical-GPU or third-party shaderpack visual result is claimed; beta.14 is experimental and the stable-release goal remains incomplete.

## PR #26 terrain tangent-space candidate — 2026-10-20

PR [#26](https://github.com/glebKovshov/Veyra/pull/26), head `b8df47e7d914374a753edb52d363ebb123110a87`, reconstructs terrain `normal`, `tangent`, `binormal`, and `viewVector` from screen-space derivatives of view position and UV. The frame is initialized at the fragment entrypoint before the pack's `main` executes. This candidate has not been merged or released.

Build run [39377074639](https://github.com/glebKovshov/Veyra/actions/runs/39377074639) passed with **221 tests, 0 failures, 0 errors, 0 skipped**, 48 example shaders processed, and 45 direct Vulkan SPIR-V compilations. The uploaded PR JAR `veyra-0.2.0-beta.14+mc1.21.11.jar` is 585,453 bytes, SHA-256 `1f4025963e5b28aa27173270aaa34d4d70335955787f6fddd1d22543eb1f22ad`; it passes `scripts/verify_jar.py`. It carries beta.14 metadata and is not a release asset.

Graphics smoke run [39377074607](https://github.com/glebKovshov/Veyra/actions/runs/39377074607) passed all 17 Lavapipe fixtures. The updated `geometry-view-space` fixture reported 222,445 active magenta pixels and 0 when shaders were disabled; runtime Vulkan validation errors were 0. Process shutdown still produced 10,031 validation diagnostics.

Pinned source audit [39377074590](https://github.com/glebKovshov/Veyra/actions/runs/39377074590) passed with 75 cases / 5,178 stages and 432 geometry fragments accepted / 285 rejected. All 75 records retain `visualValidation=false`. Complementary's `viewVector` rejection is resolved; the updated report now reaches terrain/water `mat` rejections (72 each). The remaining blockers include translucent inputs and selected compute for Complementary, cloud `texture` for BSL, and `colortex4`, attachment feedback and cloud inputs for Sildur. This is source-audit and synthetic Lavapipe evidence only; no third-party pack or physical-GPU visual result is established.

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

## Beta.15 and beta.16 publication record

Beta.15 merged the terrain tangent-space inputs (PR #26) and terrain block/material IDs (PR #27), then published tag `v0.2.0-beta.15+mc1.21.11` through PR #28. Release workflow [39382531868](https://github.com/glebKovshov/Veyra/actions/runs/39382531868) passed the pinned source audit, all 19 Lavapipe fixtures, distributable build, and JAR verification. The tag build passed 226 tests; its source audit accepted 432 and rejected 285 geometry fragments across 75 records and 5,178 stages. These results were source/synthetic checks; third-party visual validation remained untested.

Beta.16 merged the `absMidCoordPos` terrain midpoint bridge in PR #31 and published tag `v0.2.0-beta.16+mc1.21.11` via PR #32; PR #33 recorded publication. Release workflow [39387855512](https://github.com/glebKovshov/Veyra/actions/runs/39387855512) passed the pinned audit, all 19 Lavapipe fixtures, build, and JAR verifier. The tag build passed 227 tests. The published [beta.16 JAR](https://github.com/glebKovshov/Veyra/releases/download/v0.2.0-beta.16%2Bmc1.21.11/veyra-0.2.0-beta.16%2Bmc1.21.11.jar) is 590,888 bytes, SHA-256 `3574e4dc2bcfd051b7972ed2ed6e6c99359f2bc9a143ae51972bfd2718d1f487`, and matched the release checksum manifest and `scripts/verify_jar.py`. Beta.16 is experimental; physical-GPU and third-party pack visuals remain unverified.

## Beta.17 terrain-midpoint release candidate verification — 2026-10-20

On `release-prep/beta17`, JDK 21 `build validateShaders packageExamplePacks --rerun-tasks` passed locally: 229 tests, 0 failures/errors/skips; shader validation processed 50 example shaders and compiled 47 directly to Vulkan SPIR-V. `geometry-mid-texcoord` exercises the new `midCoord` input. The pinned source-audit runner completed all 75 default/profile/dimension cases and 5,178 stages: 528 accepted / 189 rejected; no case claims visual validation. The versioned beta.17 candidate passes `scripts/verify_jar.py`: 591,360 bytes, SHA-256 `ea8e5e49b205810a775832cffd7d270f8ed81d71074aae0b5fce6e2973eb9e2d`. This local hash is not the future published artifact checksum.

The initial PR checks on `eea94875` passed Build [39391504947](https://github.com/glebKovshov/Veyra/actions/runs/39391504947) and source audit [39391504835](https://github.com/glebKovshov/Veyra/actions/runs/39391504835), but Graphics smoke [39391504846](https://github.com/glebKovshov/Veyra/actions/runs/39391504846) passed only 16 of 19 fixtures. `geometry-view-space`, `geometry-mid-texcoord`, and `specular-atlas` had no active material signal and zero runtime Vulkan validation errors. Inspection traced the regression to `VRenderSystem.getTextureMatrix()` exposing a `MappedBuffer` whose native memory is not initialized. The adapter now initializes an unset/non-finite matrix to identity and preserves finite transforms; a direct-buffer regression test covers both paths.

Corrected head `f693523018977add3ecfb58ce29841b26dbfc7b7` passed Build [39393098821](https://github.com/glebKovshov/Veyra/actions/runs/39393098821), source audit [39393098886](https://github.com/glebKovshov/Veyra/actions/runs/39393098886), and all 19 Lavapipe fixtures in Graphics [39393098834](https://github.com/glebKovshov/Veyra/actions/runs/39393098834). The audit again reports 75 cases, 5,178 stages, 528 accepted / 189 rejected geometry fragments, with `visualValidation=false`. The `geometry-mid-texcoord` frame contains 55,237 active magenta pixels and 167,494 opposite-region pixels; disabled magenta is 0 and runtime Vulkan validation errors are 0. This corrected source is included in beta.17; the published asset and checksum are recorded above.
