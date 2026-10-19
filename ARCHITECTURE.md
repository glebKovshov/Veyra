# Rendering architecture and remaining integration work

The analysis used Iris commit `bff1e69cb6c5519d8745784aa9c8b92984de67e7`, VulkanMod commit `087ef24e58e5522dec5ccb44f279768786bfb6da`, and Veyra's existing Minecraft 1.21.11 code. This is a focused source audit, not a claim that every upstream path has been verified.

## Iris

`ShaderPack` discovers program sets, expands includes, reads pack properties and options, and creates dimension-specific programs. `ShaderPackOptions`, `OptionAnnotatedSource` and `JcppProcessor` determine option declarations and evaluate conditional GLSL. `ProgramSet` organizes gbuffer, shadow, prepare, deferred and composite programs. `IrisRenderingPipeline` places prepare after shadows, deferred before translucency, and composite/final after world rendering. `CompositeRenderer` tracks per-target flips and draw buffers. These OpenGL framebuffer/program objects cannot be reused directly in VulkanMod.

## VulkanMod

`VkGpuDevice.compilePipeline` converts Minecraft shader interfaces, reflects SPIR-V, creates native pipelines and maps uniform/sampler bindings to descriptors. `ExtendedRenderPipeline` holds the native `GraphicsPipeline` and emulated `EGlProgram`. The inspected `GraphicsPipeline` builds vertex/fragment stages and one color blend attachment. `Framebuffer` explicitly rejects more than one color attachment. `DefaultMainPass` and the chunk `WorldRenderer` manage rebinding the main scene target. `Pipeline.scheduleCleanUp` retires GPU objects through the frame operation queue.

Consequently, adding simultaneous geometry MRT required changes to framebuffer creation, render passes, attachment views, pipeline attachment formats/blend state, draw routing and barriers. Veyra now supplies borrowed multi-attachment framebuffers, native render passes/dynamic-rendering descriptions, attachment-aware pipeline state, and terrain draw routing. Fullscreen post outputs still use separate draws; those do not implement image-store/atomic semantics.

## Veyra

`ShaderPackRepository` discovers ZIPs/directories and restores user selection. `ShaderPack` reads bounded source snapshots, dimension overrides, include graphs, options and render-target/shadow settings. `ShaderIncludes` resolves paths inside the pack; `GlslPreprocessor` evaluates macros and conditions with JCPP after expansion, with filesystem access disabled. `ShaderPackOptions` validates and applies user overrides in memory. `ShaderOptionStore` performs atomic per-pack writes. The configuration screen only persists after successful compilation/selection.

`VeyraRenderer` compiles existing post/geometry adapters into Minecraft/VulkanMod pipelines. Its frame plans preserve auxiliary buffer sides across prepare/deferred/composite boundaries and alternate plans for temporal ping-pong. Geometry adapters synthesize supported vertex interfaces from VulkanMod's actual formats. Depth snapshots expose depth-only sampled views; new history images are cleared through `vkCmdClearDepthStencilImage` outside a render pass and transitioned to sampling. `VeyraShadowRenderer` supplies the existing shadow path. `VeyraPipelineResources` retains live synthetic objects and schedules native destruction; source-cache pruning uses mixins confined to Veyra shader identifiers.

The terrain path maps shader output locations to physical colortex indices, retains the current ping-pong side, inherits VulkanMod draw state and applies pack blend overrides per target. Sparse write masks and per-target blending require the enabled independentBlend device feature. Target sizes must match the scene. Retired framebuffer passes and their cached native pipeline variants are destroyed at frame completion. Sampled post inputs receive graph-ordered image barriers on the main command buffer rather than upload-queue transitions before their producing draws. Swapchain wrappers now preserve actual native usage flags; transfer-source usage is requested only when supported by the surface.

`VeyraSpecularTextures` resolves resource-pack `_s.png` data against the live albedo GPU texture. Atlas inputs use the existing sprite rectangles and padding; standalone SimpleTexture inputs use their resource IDs. The bounded cache owns only newly allocated material images/views and retires them after frame fences on invalidation. Static mipmaps preserve straight data channels; LabPBR category voting prevents averaging different metal identities or emissive sentinels. Native uploads use VulkanMod's image upload queue/layout tracking directly, avoiding the command encoder's global slot-0 mutation during lazy entity material creation. Entity sampler indices come from the compiled native descriptor, while terrain uses its reserved slot 1. Missing maps use zero; animated maps fail explicitly.

The material-path comparison additionally inspected Iris commit `0a1fcaae55099356bf53410b4f5302753edb0ffc`: PBRType, AtlasPBRLoader and LabPBRTextureFormat. These establish resource naming, default data and channel semantics; Veyra implements its own CPU data operations and Vulkan ownership.

## Known gaps

| Component | Current state | Needed for broad pack compatibility |
| --- | --- | --- |
| Pack discovery/options | ZIP/folder, dimensions, macros, profiles, screens, persistence | Wider option syntax/localization coverage and client UI testing |
| Geometry vertex stage | Existing synthesized interfaces | Execute arbitrary pack vertex GLSL, required attributes, IDs and varyings |
| Geometry color outputs | Native terrain MRT with per-target blend overrides; other geometry remains single-output | Entity/hand/block/cloud/shadow MRT, wider format/state semantics and runtime verification |
| Post outputs | Separate fullscreen draws per output | Genuine MRT where simultaneous execution or side effects matter |
| Textures/uniforms | Generated `noisetex`, depth/history, water opaque-depth and auxiliary-color sampling, static resource-pack specular maps, shared frame uniforms; unused samplers removed | Normal/animated material maps, auxiliary-color inputs in other geometry bridges, attachment feedback, wider shadow/depth samplers, custom texture/image units and expression uniforms |
| Additional stages | Explicit error for active unsupported stages | Compute dispatch/resources/barriers; geometry/tessellation stages and device checks |
| Pack properties | Options, target/shadow settings, program conditions | Full state/property semantics, feature flags, custom images/SSBOs |
| Runtime verification | Full build, unit tests, CPU shaderc/source audits; one Lavapipe synthetic terrain-MRT/composite client fixture passes | Physical GPUs, third-party packs, visual scenes beyond the fixture, resize/reload/switch stress and sustained memory measurements |

The synthetic client fixture confirms the terrain-MRT to composite path under Lavapipe and reports no runtime validation errors before world shutdown. It also observes 10,031 `VUID-vkDestroyDevice-device-05137` child-object diagnostics during VulkanMod/Minecraft process teardown; ownership is not identified, so process-wide cleanup is not yet validated. The broader shader-pack pipeline, other geometry families, visual correctness and leak behavior remain unconfirmed. No FPS improvement is claimed.

## Sources and licensing

- [Iris source](https://github.com/IrisShaders/Iris/tree/bff1e69cb6c5519d8745784aa9c8b92984de67e7/common/src/main/java/net/irisshaders/iris)
- [Iris material data reference](https://github.com/IrisShaders/Iris/tree/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/pbr)
- [VulkanMod source](https://github.com/xCollateral/VulkanMod/tree/087ef24e58e5522dec5ccb44f279768786bfb6da/src/main/java/net/vulkanmod)
- [Vulkan depth/stencil clear requirements](https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdClearDepthStencilImage.html)

Upstream Iris/VulkanMod code was inspected, not copied. Their LGPL licenses continue to apply to those projects. JCPP 1.4.14 is bundled under Apache-2.0; see THIRD_PARTY_NOTICES.md. External shaderpacks are not redistributed.
