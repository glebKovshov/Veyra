# Minecraft 1.21.x compatibility

Veyra builds a separate JAR for each exact Minecraft version. The table below records every stable 1.21.x release and why it is or is not a target.

| Minecraft | Pinned VulkanMod | Status |
| --- | --- | --- |
| 1.21 / 1.21.1 | 0.6.7+1.21.1 (`2eqroDKD`) | Separate JARs; full build, 706 tests, shader compilation and the same seven GPU fixtures pass on each |
| 1.21.2 / 1.21.3 | 0.5.3 (`mr9uxFGT`) | Separate JARs; full build, 706 tests, shader compilation and the same seven GPU fixtures pass on each |
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

### How 1.21.2 and 1.21.3 are supported

These versions build on the 1.21.4 backend. `src/minecraft1213/java` covers what 1.21.4 added, and `veyraLegacy3Line` in `build.gradle` covers VulkanMod 0.5.3's differences:

- **VulkanMod 0.5.3 class and API names.** Its OpenGL emulation is `Gl*` rather than `VkGl*`, and its buffers live in `vulkan.memory`. Images carry no name. Some buffer members are protected; Veyra's own buffers expose them through `LegacyBuffer`.
- **Compressed terrain vertex.** 0.5.3 stores color before UV0 and reads it as a normalized `vec4`. Veyra's extended terrain format and its terrain shader adapters follow that layout; Veyra's extra attributes keep their locations.
- **Item rendering.** Render states and the model resolver arrived in 1.21.4, so items are identified at `ItemRenderer.render`.
- **Sky draws.** The sky, its stages, weather and particles became render types in 1.21.4. In 1.21.3 the sky binds core programs directly; those binds are described by program (`VanillaRenderTypes`) and routed like render types.
- **Older Minecraft APIs:** sprite animation metadata, `SimpleTexture`, `ARGB` and the selection-list scrollbar.

`gradle/targets/1.21.3.excludes` lists the hooks for classes that do not exist on these versions. 1.21.2 and 1.21.3 share every Veyra hook target and VulkanMod build; each still gets its own exact-version JAR.

### Known limitation before 1.21.6

The shared renderer recognises the vanilla sky by the `core/sky` and `core/stars` shaders introduced in 1.21.6. On 1.21–1.21.5 the sky uses `core/position`, so the `gbuffers_skybasic` and `gbuffers_skytextured` stages are not applied. Vanilla sky rendering is unaffected. The `skybasic-vertex-execution` and `skytextured-stage-variants` fixtures fail identically on 1.21.3, 1.21.4 and 1.21.5. Weather and particle stages were not validated on these versions.

### How 1.21 and 1.21.1 are supported

These versions build on the 1.21.3 port. `src/minecraft1211/java` covers what Minecraft added in 1.21.2, and `veyraLegacy1Line` in `build.gradle` rewrites the shared sources:

- **Shader instances.** 1.21.1 has no shader manager, program keys, program configs or compiled programs. `ShaderSources` reads core shader configs and sources from the resource manager and resolves `#moj_import` the way the 1.21.1 shader instance does. `ShaderProgram` and `ShaderProgramConfig` stand in for the later records, and `ShaderInstance` takes the place of `CompiledShaderProgram`.
- **VulkanMod 0.6.7.** Its internals are 0.6.x, so the shared hooks apply rather than the 0.5.x overrides. `VkGpuDevice` compiles Veyra's pipelines with 0.6.7's GLSL parser, as VulkanMod compiles its own. A shader instance holds the uniforms so vanilla draw paths can bind the result. This parser accepts a single fragment output and no `layout` qualifiers on interface variables. Fragment outputs are therefore declared again with their locations after parsing, and uniform blocks are lowered to named uniforms.
- **Frame graph.** 1.21.1 renders the level inline. `FrameGraphBuilder`, `FramePass` and `PostPass` provide the 1.21.2 post-chain API. The level hooks run Veyra's begin, shadow and prepare passes after the fog color is set and before the main target is cleared.
- **Sky, entities and fog.** The level renderer draws the sky itself, and entities are drawn without render states. 1.21.1 has a single fog range, so the later fog-range uniform names read it. VulkanMod 0.6.7's cloud shader has the older fog block.
- **Older Minecraft APIs:** `RenderSystem.clear` and render targets take the macOS flag, `NativeImage` pixels are ABGR, and colors are vectors.

Minecraft 1.21 builds the same port against the 1.21 game (`gradle/targets/1.21.properties` sets `minecraft_release=1.21`). VulkanMod 0.6.7+1.21.1 declares both versions.

## Build an exact-version package

Use JDK 21. Target properties are in `gradle/targets/`. For example:

```powershell
.\gradlew.bat --project-cache-dir .gradle/targets/1.21.5 build validateShaders '-PtargetMinecraft=1.21.5'
python scripts/verify_jar.py --target 1.21.5
```

Replace `1.21.5` with `1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.9` or `1.21.10` for those packages. Without a target property, the default is 1.21.11:

```powershell
.\gradlew.bat --project-cache-dir .gradle/targets/1.21.11 build validateShaders
python scripts/verify_jar.py
```

On Linux/macOS use `./gradlew` in place of `.\gradlew.bat`.

| Minecraft | Distributable |
| --- | --- |
| 1.21 | `build/targets/1.21/libs/veyra-1.2.0+mc1.21.jar` |
| 1.21.1 | `build/targets/1.21.1/libs/veyra-1.2.0+mc1.21.1.jar` |
| 1.21.2 | `build/targets/1.21.2/libs/veyra-1.2.0+mc1.21.2.jar` |
| 1.21.3 | `build/targets/1.21.3/libs/veyra-1.2.0+mc1.21.3.jar` |
| 1.21.4 | `build/targets/1.21.4/libs/veyra-1.2.0+mc1.21.4.jar` |
| 1.21.5 | `build/targets/1.21.5/libs/veyra-1.2.0+mc1.21.5.jar` |
| 1.21.9 | `build/targets/1.21.9/libs/veyra-1.2.0+mc1.21.9.jar` |
| 1.21.10 | `build/targets/1.21.10/libs/veyra-1.2.0+mc1.21.10.jar` |
| 1.21.11 | `build/libs/veyra-1.2.0+mc1.21.11.jar` |

Use a distinct project cache for each target when switching versions. Shared Gradle task history can delete another target's outputs as stale. CI builds and verifies all nine targets separately.

Each JAR declares its exact Minecraft and VulkanMod dependency versions. The verifier checks metadata, entrypoints and the absence of graphics-test classes. Upload only the matching release JAR to Modrinth; source JARs and test harnesses are not installable releases.

## Validation and current limits

The build runs JUnit, pinned native adapter checks and shader compilation. GPU validation uses isolated test worlds and the Vulkan core and synchronization validation layers. Passing means the fixture's image assertions pass and its entire process log, including shutdown, has no validation errors. A successful Gradle process alone is insufficient.

The representative matrix, run on 1.21–1.21.5, covers entity MRT, begin-stage fragments, HDR compute targets, compute SSBOs, cloud model-view inputs, animated normal/specular atlases, and water with separate opaque/translucent shadow depth. These checks do not establish compatibility with every third-party shader pack.

Legacy backends keep sampler state on textures. Conflicting simultaneous sampler settings for aliases of one texture remain a limitation. On 1.21.5, native bindings currently require full-range texture views; subrange bindings fail explicitly. Independent sampler aliases and texture subranges need further implementation and graphics coverage before full pack parity can be claimed.

Agent investigation history, failed probes and detailed evidence remain in `WORK_SUMMARY.md`.
