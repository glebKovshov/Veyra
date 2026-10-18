# 0.2 development validation status

Full Fabric Loom builds passed for commits `96d6c2e82b998a38d16455f6c20833e348ca0cfb` and `35c3fde204c8050c212bccb1dd4054f96a1b3cdc` ([latest phase integration run](https://github.com/glebKovshov/Veyra/actions/runs/39134690456)). Subsequent capability diagnostics, depth-clear fixes and packaging verification require another CI run.

- 55 local JUnit tests pass with no skips or failures, using production pack/preprocessor/adapter classes on JDK 21.
- Native shaderc checks preprocess 18 bundled geometry shaders and compile 15 directly to Vulkan SPIR-V; four conditional-include compiler checks pass. This does not validate VulkanMod descriptor interfaces or full post/geometry pipeline execution.
- Four external packs were source-audited in three dimensions: all inventoried stages preprocess at default settings. Geometry adapters reject the tested pack fragments. See COMPATIBILITY.md for exact results and reproducible input hashes.
- The distributable verification script checks metadata, compiled mixin classes, refmap, settings screen, license notices and the Fabric-declared nested JCPP dependency. CI now uploads JUnit reports as well as the mod JAR.
- Runtime lifecycle, UI interaction and visual/GPU validation remain unconfirmed. No full-parity stable release is justified by these checks.

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

The local editing sandbox denies Fabric Loom's optional Unix-socket probe, so local checks were initially isolated from the Minecraft integration. The full build was subsequently verified in GitHub Actions. GPU rendering, descriptor bindings at runtime, and visual compatibility still require an in-game check.

Verify in Minecraft 1.21.11 with Fabric Loader 0.18.4+ and VulkanMod 0.6.8: pack selection, refresh after edits/removal, disabling, resource reload, window resizing, world changes, depth snapshots, and shadows on the target GPU. Review logs and visual output.

## Reproduce

Run `./gradlew --no-daemon build validateShaders packageExamplePacks` in a normal JDK 21 environment. Push a branch named `release/` plus `mod_version`, or a matching version tag, to run the gated Release workflow. It uploads the mod, sources, example packs, and SHA-256 checksums after all checks succeed.
