# 0.1.1 validation status

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
