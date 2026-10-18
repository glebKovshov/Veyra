Veyra 0.1.1 for Minecraft 1.21.11, Fabric Loader 0.18.4+ and VulkanMod **0.6.8**. Requires Java 21+.

Install `veyra-0.1.1+mc1.21.11.jar` in `mods/`. The `-sources.jar` is for developers.
Optionally extract the example-packs ZIP into `shaderpacks/`, then press F6 to select a pack.

Changes:
- Ignore commented-out includes and accept comments after include directives.
- Leave missing/recursive include diagnostics inside their GLSL conditional branches, allowing inactive branches and recursive include guards to be handled by the compiler.
- Reload the active pack when refreshing the list; retain installed pipelines if compilation fails, or disable shaders if the active pack was removed.
- Save the selected pack with an atomic file replacement where supported.
- Allow disabling shaders even when saving settings fails.
- Update active-pack labels immediately and wrap status messages.
- Release post-processing, terrain, shadow, and temporary render targets during shutdown; release temporary targets when disabling shaders.
- Add automated regression tests and a tag-triggered release workflow with checksums.
- Restrict VulkanMod to the version used by the implementation, and declare Java 21 explicitly.

Validation: the full Fabric Loom CI build passed, along with 35 JUnit tests and CPU shaderc checks (18 examples preprocessed, 15 compiled directly to Vulkan SPIR-V, and four conditional-include cases). The final release workflow repeats these checks before publication.

Compatibility remains the supported subset described in README.md. This release does not add arbitrary Iris/OptiFine shaderpack support, custom geometry vertex stages, compute shaders, or full GLSL preprocessing before interface adaptation. No in-game GPU validation was performed in the build environment; test your target pack and GPU before relying on it.
