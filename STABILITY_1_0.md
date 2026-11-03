# Veyra 1.0.0 readiness

## Current completion audit

Completed baseline: `0b94709`; the native packed-HDR follow-up is undergoing final repeats. Development metadata is still 0.2.3; no 1.0.0 tag or release has been created.

| Requirement | Authoritative evidence | Current state |
| --- | --- | --- |
| Unit, tooling, shader and package checks | `build/reports/candidate-0b94709-unit-results/`, `build/renamer-full-gate.log`, explicit `scripts/verify_jar.py` run | 670 JUnit / seven Python tests, 105 sources / 123 SPIR-V, packaging and JAR checks pass. |
| Pinned source/profile acceptance | `build/reports/stable10-renamer-source-audit/` | 75 cases, 939 geometry programs, 18 active compute stages, no rejected stages. |
| Reimagined HIGH paired rendering and lifecycle | `stable10-renamer-workingtree-veyra-reimagined-high/` and `stable10-candidate-12f1c56-iris-reimagined-high/` | Production source hashes match; all lifecycle flags and strict zero-error Vulkan/teardown checks pass. Matched Iris comparison is recorded. |
| Unbound COMPLEMENTARY, Sildur defaults and BSL HIGH natural | `build/candidate-0b94709-remaining.log`, corrected Unbound Iris report, `validation/stable-1.0-candidate.json` | All three baseline lifecycles pass. Matched references require the actual profile/clock/capture; wrong-profile Unbound HIGH is excluded. |
| Native packed-HDR follow-up | `build/reports/native-r11-unit-results/`, `stable10-native-r11-source-audit/`, `stable10-native-r11-custom-uniforms/`, `build/native-r11-candidate.log` | 671 tests, 75 audit cases and the GPU packed-format/rollback/switch/readback probe pass. BSL full lifecycle is Vulkan/teardown-clean; other profile repeats are running. |
| Rollback, cross-pack switching and native readback | Current hosted graphics matrix plus local RGB round-trip reports | Local probes pass; hosted candidate results still required. |
| Full hosted build/source/graphics gates | PR #152 checks on the candidate head | In progress at the latest verified read. Missing or failed jobs cannot be counted as passes. |
| Explain visual differences and define release scope | Matched captures, `validation/iris-bsl-phase-analysis.json`, COMPATIBILITY.md | Full parity is not certified; the residual BSL composition/reflection gap remains under analysis. |
| Versioned release and downloaded-asset verification | Release workflow, tag, downloaded checksums and JAR metadata | Not started. Select 1.0.0 metadata only after readiness is established. |

## 2026-11-03 loading-cost follow-up

The 12f1c56 Reimagined HIGH full lifecycle passes strict Vulkan and teardown gates,
but takes 9m32s. Render-thread samples show repeated GLSL declaration scans. The
local-uniform renamer now collects possible declaration names in one pass before
checking aliases, refreshing that set only after a real rewrite. On an 850,405-char
header with 300 uniform reads, the standalone unchanged-output check takes 35.4ms
instead of 3959.9ms on the same JDK 21 process setup. This is a synthetic scan
measurement, not a game loading-time or FPS claim. A 560-case differential corpus
has identical output hashes before/after, and the regression tests cover large
headers and aliases that shadow later uniform names. The current combined working
tree passes 670 JUnit tests, 105 examples / 123 SPIR-V and packaging. The pinned
audit passes all 75 cases. Primary RGB changes are committed as 0b94709, whose
production source hashes match the completed strict-zero-error Reimagined HIGH
lifecycle repeat. Unbound, Sildur and BSL candidate comparisons/lifecycles are
running sequentially; final hosted and release gates remain open.

Baseline: main `0110146`, Veyra 0.2.3 / Minecraft 1.21.11 / VulkanMod 0.6.8. This backlog does not claim 1.0.0 readiness.

| Area | Current evidence | Gap / next action |
| --- | --- | --- |
| Preprocessor | Iris exports MAX_COLOR_BUFFERS, MC_NORMAL_MAP, MC_SPECULAR_MAP; Veyra omitted these despite implementing the capabilities. Current Iris source allows 32 color buffers; Veyra allows 16 targets and 8 simultaneous outputs. | Initial fix advertises Veyra's actual limit (16) and PBR macros in GLSL/properties with branch-selection tests. Audit changed branches; expansion to 32 requires runtime and adapter work. |
| Feature negotiation | Properties now see all supported flags, while GLSL sees only supported optional declarations, matching the pinned Iris loader. SSBO macros and case-insensitive required checks are covered. | Runtime device checks remain separate; expand feature support only with rendering evidence. Optional flags must not leak between option selections or feature-on/off snapshots. |
| Custom uniforms | Scalar definitions now resolve forward references with iterative dependency ordering, preserve declaration order in shader blocks, and reject cycles before pipeline installation. AST depth is bounded for unary and flat binary chains. Float vec2/3/4 expressions now share that dependency graph, geometry suppliers and post std140 buffers. Pack switches restore previous supplier registrations. | Validate supported constructors, component access and same-width operations in tests and a mixed-width geometry/post GPU fixture. Matrix/custom integer-vector expressions and unsupported overloads remain explicitly rejected. |
| Stages and images | Geometry/tessellation and several graphics storage-image routes are explicitly rejected. Image-only packs now select the runtime feature snapshot and allocate images referenced by graphics stages independently of compute stages; shadow writes have an explicit dependency before graphics sampling. | Determine pinned-profile requirements; implement required paths and preserve clear rejection diagnostics. RTX 3070 image-only shadow/terrain/post fixture passed; final validation-layer and lifecycle checks remain. |
| Visual parity | Fresh generated scenes: BSL HIGH wall RGB MAE 2.78/255, floor 2.44, 292 matched files / Iris +0 user changes; 6b5d011 Complementary HIGH: matched 436-file tree and Iris HIGH (+0 user changes); full-frame RGB MAE 3.64/255, floor ROI 2.51. Emissive/bloom and cloud differences remain. Source audits retain visualValidation=false. | Unbound COMPLEMENTARY material wall now has a matched Iris frame (full RGB MAE 4.17/255; wall 3.86, floor 1.84). A matched natural BSL ocean frame exposed 23.35/255 MAE and an ignored texture.noise property; global custom noise now uses the existing PNG uploader and selected-pack ownership. The corrected natural BSL run is Vulkan-clean and reduces full RGB MAE to 21.34/255 across 292 byte-matched files, but water/cloud differences remain. Match animation clocks before attributing these differences, finish Sildur and final-candidate repeats. One controlled frame does not establish whole-pack parity. |
| Lifecycle | Fresh Complementary HIGH RTX lifecycle passed all stages. Khronos sync validation exposed repeated unused-target clears; the transfer dependency removed all 217 WRITE_AFTER_WRITE hazards in the image-only repeat. Core/sync native teardown leaks also reproduced without a shaderpack; draining all remaining registered resources before allocator destruction now passes both baseline/image-only repeats with zero undestroyed objects. | The three remaining acquire diagnostics were traced to AutoIndexBuffer-triggered Renderer.flushCmds before capture, not readback. An acquire semaphore relay now gives zero core/sync errors and clean teardown in baseline/image-only repeats. CI gates core/sync and teardown across 54 cases. BSL HIGH also passes all lifecycle stages with zero core/sync/teardown errors after deferring GPU-buffer retirement past pending initial uploads. First hosted sync matrix exposed early-only depth masks and missing index-upload visibility on Lavapipe, including baseline. Fixes select the exact native overload and add a graphics-buffer upload dependency; 656 tests and fresh RTX baseline/image repeats pass. Unbound COMPLEMENTARY exposed a stale VRenderSystem view overwriting noisetex during native terrain cache copy. Updating both caches and checking descriptor identity produced a complete RTX lifecycle with zero core/sync/teardown errors and confirmed recreation of its 810,549,248-byte SSBO owner. The later final-submit acquire-wait expansion still needs hosted confirmation. Repeat hosted matrix, Sildur, rollback and final-candidate checks. Current aggregate teardown reports are parsed strictly, not treated as zero leaks. |
| Release | v0.2.3 passed 622 JUnit and 5 Python tests and its publication gates | Baseline evidence is not evidence for new changes. 1.0.0 needs its own final-candidate gates and downloaded-artifact verification. |

## Iris sources

Source comparison pinned to Iris Minecraft 1.21.11 commit `0a1fcaae55099356bf53410b4f5302753edb0ffc` (resolved 2026-11-02). Historical Iris 1.10.8 capture artifacts must be distinguished from this source revision:

- [StandardMacros](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/gl/shader/StandardMacros.java)
- [FeatureFlags](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/features/FeatureFlags.java)
- [ProgramSet](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/shaderpack/programs/ProgramSet.java)
- [Feature flag contract](https://shaders.properties/current/reference/shadersproperties/flags/)
- [Pinned Iris color buffer limit](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/gl/IrisLimits.java)
- [Pinned custom uniform dependency ordering](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/uniforms/custom/CustomUniforms.java)

Float vector expressions follow the pinned Iris constructors (exactly N scalar arguments), single-component access aliases, same-width arithmetic, abs/floor/ceil/min/max/clamp and three-argument vector if. Scalar splat/mixed vector constructors, scalar-vector broadcasting and geometric functions are not added; the pinned Iris implementation also lacks these overloads. Built-in vectors and matrices cannot be replaced by custom definitions. Vector GPU buffers use GC-owned direct memory, and their registry entries are removed/restored when packs change.

- [Pinned Iris expression functions](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/parsing/IrisFunctions.java)
- [Pinned Iris vector access](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/parsing/VectorType.java)

## Release gate

Require passing unit/tooling/shader tests, source/profile audits, graphics fixtures and lifecycle checks. Supported pack/profile captures must be reproducible, with remaining Iris differences explained. Rejected features must fail before installing an incomplete replacement pipeline. Compile success or a non-black screenshot alone is insufficient.

Keep 0.2.3 metadata during development. Use feature/stable-1.0.0 for integration: pushing release/** automatically publishes after its gates, so select 1.0.0 metadata only after readiness is proven.

- [Pinned Iris custom noise resource selection](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/pipeline/CustomTextureManager.java)
- [Pinned Iris procedural noise](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/targets/backed/NativeImageBackedNoiseTexture.java)

Linux validation initially used 1.4.313. This predates [VVL synchronized-present tracking fix #10680](https://github.com/KhronosGroup/Vulkan-ValidationLayers/pull/10680), which closes [false-positive issue #10586](https://github.com/KhronosGroup/Vulkan-ValidationLayers/issues/10586). The candidate CI pin is SDK 1.4.363.0, matching the Windows validation version. The SDK archive and extracted layer/manifest are hash-checked; one prepared artifact supplies the full matrix. Retain all core/synchronization/teardown gates and require actual hosted results before classifying the remaining WRITE_AFTER_PRESENT diagnostic.

The current Sildur normal-clock lifecycle passes Khronos core/sync and teardown checks after fixing native image readback ordering and preserving the color attachment when flushCmds resumes. Native Screenshot/in-frame marker equality is checked in the uniform, SSBO and graphics-image fixtures. The matched Sildur default wall comparison has RGB MAE 10.42/255 overall (wall 2.65, floor 21.51); fixed-clock BSL natural-water/cloud differences remain at 20.35/255. These differences still require analysis and final-candidate gates are pending. Explicit cross-pack GPU switching now passes for custom uniforms, SSBO and graphics-only images: original marker ? distinct green post-only pack ? restored original marker/storage allocation, followed by disable. Each also passes failed-replacement rollback, native/in-frame screenshot equality and strict zero-error Vulkan/teardown checks; the existing three CI fixtures now require this round-trip. Evidence: build/reports/stable10-cross-pack-r2-*/.

Hosted commit `4fbdc6b` passes Build #1068, source audit #302 and every Graphics smoke #348 job (55/55: preparation/device baseline plus 54 graphics cases) using SDK 1.4.363.0. The checksum-verified layer runs with the unchanged core/sync/teardown gates; the old WRITE_AFTER_PRESENT baseline finding does not reproduce. The cross-pack additions require their subsequent hosted run before final-candidate evidence is complete.

The dominant Sildur floor difference is now diagnosed and corrected: its `sunPathRotation=-40` const directive is inside a block comment. Veyra had stripped that directive and used zero. Like [pinned Iris ConstDirectiveParser](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/shaderpack/parsing/ConstDirectiveParser.java), Veyra now accepts line-start const directives there, including editable constant options, while keeping inactive branches/line comments out of renderer settings. Fresh matched fixed-clock Sildur frames (137 identical files) reduce RGB MAE from 10.425969 to 4.341830 overall and from 21.368712 to 4.300185 on the floor. Shadow model-view differs by at most 2e-7; shadow projection and camera model-view match. Both harnesses save matrices with the captured frame; raw native/OpenGL projection arrays require convention conversion. A corrected normal-clock Sildur full lifecycle is core/sync/teardown-clean. This resolves the missing wall shadow, not every remaining visual difference or the BSL natural ocean gap.

The bright BSL horizon band is also explained: [Iris HorizonRenderer](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/pathways/HorizonRenderer.java) draws a camera-relative cone below the upper sky plane, while vanilla leaves that region at clear color. Veyra now draws that cone through the existing shader SKY pass before the original plane. Fresh matched fixed-clock HIGH natural-ocean frames have 292 identical files and reduce full RGB MAE from 20.343329 to 9.027627/255. Camera/shadow matrices agree within 2e-7 apart from native projection conventions. The skybasic fixture now looks at the horizon and requires at least 1,000 marker pixels in a four-row strip above its center: corrected run has 2,560; a negative control without the cone has zero despite 108,672 ordinary sky marker pixels. Both are Vulkan/teardown-clean, with the negative control correctly rejected for missing image content. Remaining cloud and water-reflection differences still need analysis; the horizon fix does not establish full BSL parity.

Current horizon candidate also passes normal-clock BSL HIGH natural lifecycle with zero core/sync/teardown diagnostics, and a repeated fixed-clock Sildur wall frame remains clean at RGB MAE 4.324512 overall / 4.292855 floor. The local full build/shader gate passes 663 JUnit tests and seven Python tests. Hosted 62d701c passes Build #1072, source audit #304 and Graphics smoke #350 (55/55 jobs); the horizon addition requires its subsequent hosted run. Remaining water/cloud causes, final candidate repeats, version metadata and release publication remain open.

The large BSL reflection distortion is now traced to inconsistent geometry sampling matrices: forward projection used native clip coordinates while its inverse used the screen-texture convention. Fragment/compute adapters now bind current and previous forward projections with the same conversion as that inverse. Existing vertex clip-position adapters retain their native matrices. The custom-uniform fixture checks a real GPU forward/inverse round-trip; its corrected rollback/cross-pack/native screenshot probe passes with 222,926 equal native/in-frame marker pixels. A negative control restoring the old mismatch has zero active markers and correctly fails, with clean Vulkan teardown. BSL HIGH natural full RGB MAE falls from 9.027627 to 3.838811/255 across 292 identical files, removing the large water reflection artifacts. This does not establish full parity: residual cloud/sampling/history differences and final-candidate gates remain. The local source audit passes all 75 cases (939 geometry fragments and 18 active compute stages; zero source errors), and test/build/shader gates pass 664 JUnit and seven Python tests.

The sampling-projection candidate also passes the complete BSL HIGH natural lifecycle (zero core/sync/teardown diagnostics) and a Sildur wall repeat at RGB MAE 4.305195 overall / 4.261330 floor. The SSBO compute fixture independently passes its GPU projection round-trip, rollback, cross-pack switch and native screenshot equality (230,400 pixels). An identical-settings Iris BSL repeat differs by only 0.081525/255, so the remaining 3.838811 Veyra/Iris difference cannot be dismissed as reference repeatability. Further cloud/sampling analysis and final candidate/release gates remain open. Hosted fea7bf7 passed Build #1074, source audit #305 and Graphics smoke #351 (55/55 jobs); the sampling-projection fix requires its hosted repeat.

Legacy color-buffer directives now share the existing sampler alias table (`gcolor`, `gdepth`, `gnormal`, `composite`, `gaux1..4`) for Format/Clear/ClearColor, including active block-comment const lines, matching [Iris render-target directives](https://github.com/IrisShaders/Iris/blob/0a1fcaae55099356bf53410b4f5302753edb0ffc/common/src/main/java/net/irisshaders/iris/shaderpack/properties/PackRenderTargetDirectives.java). RGB10_A2 now has native packed allocation/size/feature validation and a wide fallback; intermediate readback supports packed 10-bit and RGBA16 UNORM. BSL confirms physical R8=9, RGB10_A2=64 and RGBA16=91 across 40 captured intermediate images with zero Vulkan/teardown errors. The custom-uniform fixture requires physical packed/wide storage for gdepthFormat, then passes rollback/switch/screenshot equality at 222,709 pixels. Local gates pass 666 JUnit tests, seven Python tests, 105 examples / 123 SPIR-V and all 75 pinned audit cases (939 geometry programs, 18 active compute stages, zero source errors). Full BSL lifecycle and a Sildur initial repeat remain clean. BSL natural full RGB MAE is 3.980409/255 (previous 3.838811); this repairs an ignored format contract rather than resolving every residual cloud difference. A modified Iris diagnostic approximating old storage shifts the reference by 1.529868/255; it is not an unmodified parity baseline.

Camera cloud height now follows the actual Iris 1.10.8 supplier: camera environment
attribute probe at the frame partial tick, rather than the dimension-wide default.
The custom-uniforms world supplies a biome override of 224 versus dimension 192.33;
terrain and post both require 224. The final local r3 probe passes rollback,
cross-pack switching and native/in-frame equality at 222,038 pixels, with zero
core/sync/teardown errors. Restoring the old supplier makes the capture gate fail;
that negative control is also Vulkan-clean. Both reference harnesses record the
camera/dimension cloud heights beside their matrices. Fresh original BSL HIGH
natural captures report 192.33 in both and full RGB MAE 3.921677/255, so this defect
does not explain that scene's remaining difference. Native-depth and cloud Bayer-Y
diagnostics also leave the dominant residual unresolved. Hosted 871fe07 passes
Build #1078, source audit #307 and all 55 Graphics smoke #353 jobs. The new camera
attribute change still requires its own hosted checks and final-candidate gates.

The experimental-world backup-screen errors are reproduced by holding that screen
for 20 ticks. LoadingOverlay clears the last presented swapchain texture through
VkCommandEncoder's standalone command path while no image is acquired. The shared
encoder mixin now begins a normal Renderer frame for a swapchain clear outside
recording, then substitutes the newly acquired frame's main color texture. The
native inRenderPass validation and offscreen clear paths retain their behavior.
The GPU fixture requires that screen delay and guards swapchain image transitions.
Its corrected probe passes rollback/cross-pack/native screenshot checks at 222,800
pixels, with zero core/sync/teardown errors. The ordinary vanilla baseline and full
normal-clock BSL natural lifecycle are also clean. Full JDK 21 build/test/SPIR-V,
seven Python tests and explicit JAR verification pass; hosted verification of the
new fix remains required. Keep the original failing logs as negative evidence.

A corrected TAA-off diagnostic changes both original-source defaults and the HIGH
profile in both owned copies; all 292 files match and Iris confirms HIGH (+0 user
changes). Its full RGB MAE is 3.741655/255, versus 3.921677 with TAA. Turning TAA
off shifts Veyra by 0.848611 and Iris by 0.805192. This rules out TAA as the sole
cause; cloud/noise sampling remains to be isolated. The earlier profile-only
TAA control left Iris on its original defaults and is excluded from conclusions.

Direct noise sampling clarifies the remaining scope: a final-pass tiled noisetex
probe differs by 35.432714/255 before accounting for screen UV origin and only
0.014103 after reflecting screen Y. A second probe reconstructs the world ray using
both inverse projection and inverse model-view, then samples the original noise
PNG from that ray. Across 292 matched modified files it differs by 0.020457/255,
with matching HIGH/defaults, fixed clocks/scenes and clean Vulkan teardown. These
controls support the input/noise/projection path but are modified diagnostic output,
not proof of full BSL parity. Cloud marching, masking/composition and intermediate
history remain to be isolated before the final candidate comparison.

The remaining raw cloud-density difference is now isolated: deferred1 alpha is
stored in a separate RGBA16F colortex12, then displayed without later processing.
All 292 modified files match. Raw alpha MAE is 1.148845 full / 3.181418 in sky
rows 0:130. Mirroring only Iris's Bayer Y coordinate reduces this to 0.000234 /
0.000649, explaining its sample-phase difference. These intentionally grayscale
captures fail the unchanged normal color-diversity gate; they are diagnostic data
with clean Vulkan/teardown, not successful original-pack visual validation.

Actual Iris 1.10.8 RenderTarget enables linear filtering for non-integer color
attachments. Veyra's shared geometry auxiliary binding forced nearest filtering;
it now uses clamp-to-edge plus linear for its supported color formats. The existing
water-color-inputs fixture writes alternating blue/red deferred pixel columns and
requires their halfway blend through gaux2 and colortex5. Old nearest filtering
fails with zero marker pixels; corrected filtering passes at 222,757 / zero disabled
markers, both Vulkan-clean. Original BSL full RGB MAE is now 3.825747 (water rows
145:360=4.586129), Sildur default remains 4.313471 full / 4.299621 floor across 137
matched files. Full local build/test/shader checks and BSL lifecycle pass. Cloud
color/composition and water-reflection differences plus final-candidate repeats
remain; density agreement alone does not establish full visual parity.

Hosted e68809f Build #1082 and source audit #309 pass. Graphics #355 has 54 successful
jobs and one dependency-resolution failure before Minecraft launch (geometry-mrt,
job 125585640665); its raw error is retained. The sampler fix needs a fresh complete
hosted matrix before release. No validation or readback gate was relaxed.

RGB storage fallbacks now retain their logical channel contract: sampling RGB8,
RGB8_SNORM, RGB16F or R11F_G11F_B10F through an RGBA attachment returns alpha one,
regardless of fragment output alpha. Only sampled descriptors use the swizzled
view; attachment and storage-image views retain their native layout. Pass-start
snapshots inherit the contract, including all mip levels; resize/replacement and
pack disable retire extra views after the frame fence. The extended water fixture
deliberately writes alpha zero and checks geometry reads and mip-level-1 post reads.
The old path has zero markers and fails; the corrected RTX 3070 run has 222,757
active / zero disabled markers. Both have zero core/sync/teardown errors.

The local gate now passes 668 JUnit tests, seven Python tests, 105 example sources /
123 SPIR-V compilations, packaging and explicit JAR verification. Original BSL HIGH
natural repeat has 292 byte-identical shader files versus Iris, matching fixed
clocks and clean validation. Its full RGB MAE is 3.865272/255 (water rows 145:360:
4.652299); this is a channel-contract correction, not a claim that the residual
visual gap is solved. The full normal-clock BSL natural lifecycle also passes
resize, Nether/Overworld return, disable/re-enable and resource reload, with zero
core/sync/teardown diagnostics. The pinned audit passes all 75 cases, 939 geometry
fragments and 18 active compute stages. Reports: build/reports/stable10-rgb-alpha-*/.
The RGB fixture also passes rollback, cross-pack round-trip and native screenshot
equality with clean teardown; CI now requires these probes for water-color-inputs.
Hosted final-head checks and remaining final-candidate pack comparisons are pending.
