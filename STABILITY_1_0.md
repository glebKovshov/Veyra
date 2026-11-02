# Veyra 1.0.0 readiness

Baseline: main `0110146`, Veyra 0.2.3 / Minecraft 1.21.11 / VulkanMod 0.6.8. This backlog does not claim 1.0.0 readiness.

| Area | Current evidence | Gap / next action |
| --- | --- | --- |
| Preprocessor | Iris exports MAX_COLOR_BUFFERS, MC_NORMAL_MAP, MC_SPECULAR_MAP; Veyra omitted these despite implementing the capabilities. Current Iris source allows 32 color buffers; Veyra allows 16 targets and 8 simultaneous outputs. | Initial fix advertises Veyra's actual limit (16) and PBR macros in GLSL/properties with branch-selection tests. Audit changed branches; expansion to 32 requires runtime and adapter work. |
| Feature negotiation | Properties now see all supported flags, while GLSL sees only supported optional declarations, matching the pinned Iris loader. SSBO macros and case-insensitive required checks are covered. | Runtime device checks remain separate; expand feature support only with rendering evidence. Optional flags must not leak between option selections or feature-on/off snapshots. |
| Custom uniforms | Scalar definitions now resolve forward references with iterative dependency ordering, preserve declaration order in shader blocks, and reject cycles before pipeline installation. AST depth is bounded for unary and flat binary chains. Vec2/3/4 are still rejected. | Integrate typed vector parsing, evaluation and uniform-buffer layout using the corrected dependency graph. Validate graphics and post consumers together. |
| Stages and images | Geometry/tessellation and several graphics storage-image routes are explicitly rejected. Image allocation in VeyraComputePipelineSet.compile is tied to active compute files. | Determine pinned-profile requirements; implement required paths and preserve clear rejection diagnostics. Investigate image-only packs and withRuntimeFeatures selection before claiming general image support. |
| Visual parity | Historical generated-scene tests retain lighting/water/color differences; audit records have visualValidation=false | Repeat matched comparisons on the current candidate; diagnose matrices, color formats, blending, sampling and pass ordering. Include natural-world scenes. |
| Lifecycle | Selected RTX lifecycle evidence and 54 synthetic graphics fixtures exist | Repeat final-candidate lifecycle, Vulkan validation, failed-pack rollback and repeated reload/resize ownership checks. |
| Release | v0.2.3 passed 622 JUnit and 5 Python tests and its publication gates | Baseline evidence is not evidence for new changes. 1.0.0 needs its own final-candidate gates and downloaded-artifact verification. |

## Iris sources

Source comparison pinned to Iris Minecraft 1.21.11 commit `0a1fcaae55099356bf53410b4f5302753edb0ffc` (resolved 2026-11-02). Historical Iris 1.10.8 capture artifacts must be distinguished from this source revision:

- [StandardMacros](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/gl/shader/StandardMacros.java)
- [FeatureFlags](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/features/FeatureFlags.java)
- [ProgramSet](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramSet.java)
- [Feature flag contract](https://shaders.properties/current/reference/shadersproperties/flags/)
- [Pinned Iris color buffer limit](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/gl/IrisLimits.java)
- [Pinned custom uniform dependency ordering](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/uniforms/custom/CustomUniforms.java)

## Release gate

Require passing unit/tooling/shader tests, source/profile audits, graphics fixtures and lifecycle checks. Supported pack/profile captures must be reproducible, with remaining Iris differences explained. Rejected features must fail before installing an incomplete replacement pipeline. Compile success or a non-black screenshot alone is insufficient.

Keep 0.2.3 metadata during development. Use feature/stable-1.0.0 for integration: pushing release/** automatically publishes after its gates, so select 1.0.0 metadata only after readiness is proven.
