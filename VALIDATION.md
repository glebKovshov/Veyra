# 0.2 development validation status

The native MRT implementation and graph-ordered sampled-image transitions passed the full Fabric Loom build, tests, remapping and distributable verification at commit `00c2054c359eca4bb7dab2c2e142b403b6c1184d` ([build](https://github.com/glebKovshov/Veyra/actions/runs/39144704660)). This is a development artifact, not a stable release.

- 72 JUnit tests pass with no failures or skips, including pinned VulkanMod bytecode contracts, draw-buffer mappings and per-target blend properties.
- Native shaderc checks preprocess 19 examples and compile 16 directly to SPIR-V. Four conditional-include compiler checks and an unbound-output dataflow compiler check also pass. These checks do not establish descriptor/runtime correctness.
- Four external packs were source-audited in three dimensions. All inventoried stages preprocess at defaults, but the restricted geometry adapters reject the tested pack fragments. All 21 declared profiles were also audited across those dimensions (63 cases, 4,429 stages). See COMPATIBILITY.md.
- A new isolated test mod launches Minecraft 1.21.11 with VulkanMod 0.6.8, an original generated flat world, Mesa Lavapipe and the Vulkan validation layer. It compares the fixture's auxiliary MRT color against disabled shaders and uploads images, logs and a machine-readable result. The harness is excluded from the distributed mod.
- [Graphics run 39144700849](https://github.com/glebKovshov/Veyra/actions/runs/39144700849) loaded the world but **failed**: active/disabled captures were both black and image-layout validation errors remained. The next revision moves readback into the producing graphics submission and waits for its frame fence; its outcome must be checked separately.
- Physical GPU testing, successful visual MRT output, UI interaction, resize/reload stress and popular-pack visual compatibility remain unconfirmed. No full-parity stable release is justified.

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

The local editing sandbox denies Fabric Loom's optional Unix-socket probe, so local checks were initially isolated from the Minecraft integration. The full build was subsequently verified in GitHub Actions. Software-Vulkan client testing now runs in CI, but its failing results above do not establish working rendering or descriptor correctness. Physical GPU validation remains necessary.

Verify in Minecraft 1.21.11 with Fabric Loader 0.18.4+ and VulkanMod 0.6.8: pack selection, refresh after edits/removal, disabling, resource reload, window resizing, world changes, depth snapshots, and shadows on the target GPU. Review logs and visual output.

## Reproduce

Run `./gradlew --no-daemon build validateShaders packageExamplePacks` in a normal JDK 21 environment. Push a branch named `release/` plus `mod_version`, or a matching version tag, to run the gated Release workflow. It uploads the mod, sources, example packs, and SHA-256 checksums after all checks succeed.
