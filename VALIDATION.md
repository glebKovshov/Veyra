# 0.2 development validation status

## Local continuation checks — 2026-10-19

On top of `e42f151`, JDK 21.0.12.1 ran `test --rerun-tasks` (92 tests, zero failures/errors/skips) and `build validateShaders packageExamplePacks` successfully. `scripts/verify_jar.py build/libs/veyra-0.2.0+mc1.21.11.jar` verified the remapped JAR, metadata, mixins, nested JCPP and exclusion of the graphics harness. Historical JARs remain in the local build directory; an explicit path avoids selecting stale artifacts.

The include expander now bounds total recursive work, in addition to depth and output size. The pinned audit accepts a JSON argument file to avoid Windows command-length limits. Source audit and Lavapipe workflows are reusable release prerequisites and run on relevant pull requests. The audit records unsupported features; a green audit job is not a claim that those packs run.

Windows UI validation is blocked: `sky.list_apps()` returned `Computer Use native pipe is unavailable: failed to connect native pipe: Не удается найти указанный файл. (os error 2)`. No current physical-GPU/UI compatibility result is claimed. The prior graphics and source results below are historical and retain their original source commit attribution.


The current feature branch passed the full Fabric Loom build, tests, remapping and distributable verification at commit `340db0ca80010d42591383b13e12d76463467048` in both the [push run](https://github.com/glebKovshov/Veyra/actions/runs/39166689216) and the [PR run](https://github.com/glebKovshov/Veyra/actions/runs/39166691696). This produces a development artifact, not a stable release.

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
