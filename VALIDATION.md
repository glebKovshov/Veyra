# 0.1.1 validation status

This branch is a release candidate. It has not been published or tested in a running Minecraft client.

## Completed

- JDK 21 compilation of the production pack loader, include expander, settings records, and all GLSL adapters.
- 35 JUnit regression tests passed, with no failures or skips. Coverage includes numeric pass ordering, wrapped ZIPs, active-selection preservation, failed configuration writes, include expansion limits, path traversal rejection, comments, and 18 bundled geometry examples.
- The new `validateShaders` Gradle task passed in an isolated Java build using the actual production classes. shaderc preprocessed 18 examples and compiled 15 directly to Vulkan SPIR-V with automatic location/binding assignment. Three examples need VulkanMod's separate uniform/descriptor conversion and were only preprocessed, not compiled to SPIR-V by this check.
- Four shaderc compiler checks passed: inactive missing include accepted, active missing include rejected, guarded recursion accepted, unguarded recursion rejected.
- Both GitHub Actions workflows parsed as YAML, and their shell steps passed `bash -n`.
- `git diff --check` passed.

## Blocked in the editing environment

- The full Fabric Loom build stops in `net.fabricmc.loom.util.CurrentPlatform.isUnixDomainSocketsSupported` with `java.net.SocketException: Operation not permitted`. The sandbox denies the optional Unix-socket probe. The isolated checks do not compile the Minecraft/VulkanMod integration or substitute for the full build. No installable mod JAR was produced here.
- GitHub rejected creating the remote branch with `403 Resource not accessible by integration`. Git push has no available credentials. No remote branch, tag, PR, or release was created.

## Before publishing

Run `./gradlew --no-daemon build validateShaders packageExamplePacks` in CI or a normal JDK 21 environment. Verify in Minecraft 1.21.11 with Fabric Loader 0.18.4+ and VulkanMod 0.6.8: pack selection, refresh after edits/removal, disabling, resource reload, window resizing, world changes, depth snapshots, and shadows on the target GPU. Review logs and visual output.

After successful validation, push the branch and the matching tag `v0.1.1+mc1.21.11`; the prepared Release workflow builds and tests the tagged commit before uploading assets.
