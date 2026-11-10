# Minecraft 1.21.x compatibility

Veyra builds a separate JAR for each exact Minecraft version. The table below records every stable 1.21.x release and why it is or is not a target.

| Minecraft | Pinned VulkanMod | Status |
| --- | --- | --- |
| 1.21 / 1.21.1 | 0.6.7+1.21.1 (`2eqroDKD`) | Not supported yet (see below) |
| 1.21.2 / 1.21.3 | 0.5.3 (`mr9uxFGT`) | Not supported yet (see below) |
| 1.21.4 | 0.5.6 (`RAtVo03C`) | Full build, 706 tests, shader compilation and the same seven GPU fixtures as 1.21.5 pass |
| 1.21.5 | 0.5.6 (`7pYvUnJk`) | Full build, 707 tests, shader compilation and seven representative GPU fixtures pass |
| 1.21.6 / 1.21.7 / 1.21.8 | None released | Not supported: VulkanMod has no release for these versions |
| 1.21.9 / 1.21.10 | 0.6.6 (`jkDMvvWU`) | Separate JARs build and verify; seven representative GPU fixtures pass |
| 1.21.11 | 0.6.8+1.21.11 (`hWKRTXbQ`) | Existing supported target |

Dependency version IDs are pinned from the [VulkanMod version API](https://api.modrinth.com/v2/project/JYQhtZtO/version). As of 2026-11-09 VulkanMod has no release for 1.21.6–1.21.8. Supporting them would first require porting VulkanMod itself.

### How 1.21.4 is supported

Veyra hooks Minecraft's GPU abstraction (`com.mojang.blaze3d.textures`, `RenderPipeline`, `GpuDevice`, `CommandEncoder`), introduced in 1.21.5, and VulkanMod's implementation of it in `net.vulkanmod.render.engine`. Neither exists in 1.21.4: a draw there is a render type applying global state, then a shader program bind, and VulkanMod 0.5.6 intercepts that path.

`src/minecraft1214/java` adds a small backend in `dev.veyra.compat` that provides those types on top of the 1.21.4 path. The shared renderer is compiled against it through source rewrites in `build.gradle` (`veyraLegacy4Line`).

- `VkGpuDevice` compiles Veyra's pipeline descriptions with VulkanMod's GLSL converter into shader programs. It numbers vertex inputs by vertex format and keeps the 1.21.5 sampler slots for `Sampler0`–`7`.
- `VanillaPipelines` describes vanilla render types as the pipelines 1.21.5 later made of them, so routing by pipeline name and shader works unchanged. Shader comparisons go through `VanillaPipelines.shaderIs`, because 1.21.5 merged several per-render-type shaders (for example `core/entity`).
- `LegacyDraws` routes draws where a render type applies its state and where a program is bound. Those are the two points 1.21.4 offers instead of a render pass.
- Post passes take their inputs, Veyra's uniform values and their pipeline's fixed-function state in a 1.21.4 `PostPass` input. On this version, VulkanMod reads blend, depth and write state from global render state, not from the pipeline.
- Render targets expose texture ids. They are wrapped as texture handles, read without VulkanMod's clear-on-read side effect, and re-attached when Veyra replaces an attachment.

Behaviour that differs from OpenGL in VulkanMod's emulation is handled explicitly:

- A clear with no framebuffer bound targets the main target.
- Creating or resizing a render target, and Veyra binding a draw's inputs, never leave a render pass open across copies or layout barriers.
- Sampled depth-stencil images get depth-only views.

`gradle/targets/1.21.4.excludes` lists shared hooks for classes that do not exist on 1.21.4.

### Why 1.21–1.21.3 are not targets yet

1.21.2/1.21.3 pin VulkanMod 0.5.3 and 1.21/1.21.1 pin 0.6.7+1.21.1, each with its own renderer internals and Minecraft shader API (1.21.1 predates `CompiledShaderProgram`). Each needs its own port and GPU validation; the 1.21.4 backend is the starting point.

## Build an exact-version package

Use JDK 21. Target properties are in `gradle/targets/`. For example:

```powershell
.\gradlew.bat --project-cache-dir .gradle/targets/1.21.5 build validateShaders '-PtargetMinecraft=1.21.5'
python scripts/verify_jar.py --target 1.21.5
```

Replace `1.21.5` with `1.21.4`, `1.21.9` or `1.21.10` for those packages. Without a target property, the default is 1.21.11:

```powershell
.\gradlew.bat --project-cache-dir .gradle/targets/1.21.11 build validateShaders
python scripts/verify_jar.py
```

On Linux/macOS use `./gradlew` in place of `.\gradlew.bat`.

| Minecraft | Distributable |
| --- | --- |
| 1.21.4 | `build/targets/1.21.4/libs/veyra-1.1.0+mc1.21.4.jar` |
| 1.21.5 | `build/targets/1.21.5/libs/veyra-1.1.0+mc1.21.5.jar` |
| 1.21.9 | `build/targets/1.21.9/libs/veyra-1.1.0+mc1.21.9.jar` |
| 1.21.10 | `build/targets/1.21.10/libs/veyra-1.1.0+mc1.21.10.jar` |
| 1.21.11 | `build/libs/veyra-1.1.0+mc1.21.11.jar` |

Use a distinct project cache for each target when switching versions. Shared Gradle task history can delete another target's outputs as stale. CI builds and verifies all five targets separately.

Each JAR declares its exact Minecraft and VulkanMod dependency versions. The verifier checks metadata, entrypoints and the absence of graphics-test classes. Upload only the matching release JAR to Modrinth; source JARs and test harnesses are not installable releases.

## Validation and current limits

The build runs JUnit, pinned native adapter checks and shader compilation. GPU validation uses isolated test worlds and the Vulkan core and synchronization validation layers. Passing means the fixture's image assertions pass and its entire process log, including shutdown, has no validation errors. A successful Gradle process alone is insufficient.

The representative 1.21.5 and 1.21.4 matrix covers entity MRT, begin-stage fragments, HDR compute targets, compute SSBOs, cloud model-view inputs, animated normal/specular atlases, and water with separate opaque/translucent shadow depth. These checks do not establish compatibility with every third-party shader pack.

Legacy backends keep sampler state on textures. Conflicting simultaneous sampler settings for aliases of one texture remain a limitation. On 1.21.5, native bindings currently require full-range texture views; subrange bindings fail explicitly. Independent sampler aliases and texture subranges need further implementation and graphics coverage before full pack parity can be claimed.

Agent investigation history, failed probes and detailed evidence remain in `WORK_SUMMARY.md`.
