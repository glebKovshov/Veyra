# Rendering architecture and remaining integration work

The analysis used Iris commit `bff1e69cb6c5519d8745784aa9c8b92984de67e7`, VulkanMod commit `087ef24e58e5522dec5ccb44f279768786bfb6da`, and Veyra's existing Minecraft 1.21.11 code. This is a focused source audit, not a claim that every upstream path has been verified.

## Iris

`ShaderPack` discovers program sets, expands includes, reads pack properties and options, and creates dimension-specific programs. `ShaderPackOptions`, `OptionAnnotatedSource` and `JcppProcessor` determine option declarations and evaluate conditional GLSL. `ProgramSet` organizes gbuffer, shadow, prepare, deferred and composite programs. `IrisRenderingPipeline` places prepare after shadows, deferred before translucency, and composite/final after world rendering. `CompositeRenderer` tracks per-target flips and draw buffers. These OpenGL framebuffer/program objects cannot be reused directly in VulkanMod.

## VulkanMod

`VkGpuDevice.compilePipeline` converts Minecraft shader interfaces, reflects SPIR-V, creates native pipelines and maps uniform/sampler bindings to descriptors. `ExtendedRenderPipeline` holds the native `GraphicsPipeline` and emulated `EGlProgram`. The inspected `GraphicsPipeline` builds vertex/fragment stages and one color blend attachment. `Framebuffer` explicitly rejects more than one color attachment. `DefaultMainPass` and the chunk `WorldRenderer` manage rebinding the main scene target. `Pipeline.scheduleCleanUp` retires GPU objects through the frame operation queue.

Consequently, simultaneous geometry MRT requires changes to framebuffer creation, render passes, attachment views, pipeline attachment formats/blend state, draw routing and barriers. Splitting fullscreen post outputs into separate draws, as Veyra currently does, does not implement geometry MRT or image-store/atomic semantics.

## Veyra

`ShaderPackRepository` discovers ZIPs/directories and restores user selection. `ShaderPack` reads bounded source snapshots, dimension overrides, include graphs, options and render-target/shadow settings. `ShaderIncludes` resolves paths inside the pack; `GlslPreprocessor` evaluates macros and conditions with JCPP after expansion, with filesystem access disabled. `ShaderPackOptions` validates and applies user overrides in memory. `ShaderOptionStore` performs atomic per-pack writes. The configuration screen only persists after successful compilation/selection.

`VeyraRenderer` compiles existing post/geometry adapters into Minecraft/VulkanMod pipelines. Its frame plans preserve auxiliary buffer sides across prepare/deferred/composite boundaries and alternate plans for temporal ping-pong. Geometry adapters synthesize supported vertex interfaces from VulkanMod's actual formats. Depth snapshots expose depth-only sampled views; new history images are cleared through `vkCmdClearDepthStencilImage` outside a render pass and transitioned to sampling. `VeyraShadowRenderer` supplies the existing shadow path. `VeyraPipelineResources` retains live synthetic objects and schedules native destruction; source-cache pruning uses mixins confined to Veyra shader identifiers.

## Known gaps

| Component | Current state | Needed for broad pack compatibility |
| --- | --- | --- |
| Pack discovery/options | ZIP/folder, dimensions, macros, profiles, screens, persistence | Wider option syntax/localization coverage and client UI testing |
| Geometry vertex stage | Existing synthesized interfaces | Execute arbitrary pack vertex GLSL, required attributes, IDs and varyings |
| Geometry color outputs | Single scene target | Native simultaneous MRT, per-target blending/clear/flip/state |
| Post outputs | Separate fullscreen draws per output | Genuine MRT where simultaneous execution or side effects matter |
| Textures/uniforms | Existing restricted sampler and uniform sets | Noise/custom textures/images, extra depth/shadow/color inputs, custom expression uniforms |
| Additional stages | Explicit error for active unsupported stages | Compute dispatch/resources/barriers; geometry/tessellation stages and device checks |
| Pack properties | Options, target/shadow settings, program conditions | Full state/property semantics, feature flags, custom images/SSBOs |
| Runtime verification | Full build, unit tests, CPU shaderc/source audits | Minecraft launch, GPU validation, visual scenes, resize/reload/switch stress and memory measurements |

The new pipeline cleanup and phase scheduling compile against the pinned backend but have not run on a Vulkan device. Their runtime correctness and leak behavior remain unconfirmed. No FPS improvement is claimed.

## Sources and licensing

- [Iris source](https://github.com/IrisShaders/Iris/tree/bff1e69cb6c5519d8745784aa9c8b92984de67e7/common/src/main/java/net/irisshaders/iris)
- [VulkanMod source](https://github.com/xCollateral/VulkanMod/tree/087ef24e58e5522dec5ccb44f279768786bfb6da/src/main/java/net/vulkanmod)
- [Vulkan depth/stencil clear requirements](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdClearDepthStencilImage.html)

Upstream Iris/VulkanMod code was inspected, not copied. Their LGPL licenses continue to apply to those projects. JCPP 1.4.14 is bundled under Apache-2.0; see THIRD_PARTY_NOTICES.md. External shaderpacks are not redistributed.
