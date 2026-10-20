# Shader support reference

This document describes which parts of the Iris shader pack format Veyra currently supports. It describes the current `main` branch; see the [changelog](../CHANGELOG.md) for when each feature was added.

Veyra is a compatibility layer in progress, not a general Iris or OptiFine port. Any program, input, sampler, or uniform not listed here should be treated as unsupported. Unsupported elements in an active program produce an explicit error instead of being silently ignored.

- [Pack loading](#pack-loading)
- [Geometry programs](#geometry-programs)
- [Shadow programs](#shadow-programs)
- [Post-processing programs](#post-processing-programs)
- [Samplers](#samplers)
- [Uniforms](#uniforms)
- [Not implemented](#not-implemented)

## Pack loading

### Recognized programs

Veyra lists a folder or ZIP archive as a shader pack when it contains at least one of these fragment programs under `shaders/`:

| Family | Programs |
| --- | --- |
| Terrain | `gbuffers_terrain`, `gbuffers_terrain_solid`, `gbuffers_terrain_cutout`, `gbuffers_water`, `gbuffers_damagedblock` |
| Entities | `gbuffers_entities`, `gbuffers_entities_translucent`, `gbuffers_spidereyes` |
| Hand | `gbuffers_hand`, `gbuffers_hand_water` |
| Block entities | `gbuffers_block`, `gbuffers_block_translucent` |
| Particles and weather | `gbuffers_particles`, `gbuffers_particles_translucent`, `gbuffers_weather`, `gbuffers_lightning` |
| Lines | `gbuffers_basic`, `gbuffers_line` |
| Sky and clouds | `gbuffers_skybasic`, `gbuffers_skytextured`, `gbuffers_clouds` |
| Other | `gbuffers_armor_glint`, `gbuffers_beaconbeam`, `gbuffers_textured`, `gbuffers_textured_lit` |
| Shadow | `shadow`, `shadow_solid`, `shadow_cutout`, `shadow_water`, `shadow_entities`, `shadow_block` |
| Post-processing | `prepare`–`prepare99`, `deferred`–`deferred99`, `composite`–`composite99`, `final` |

All programs use the `.fsh` extension. Vertex stages (`.vsh`) are supported only for clouds and post-processing programs; see [Not implemented](#not-implemented).

### Includes and preprocessing

- Quoted and angle-bracket `#include` directives are expanded recursively. Paths resolve relative to the including file, or from the `shaders/` root when they start with `/`. Includes cannot escape the pack.
- Commented-out directives are ignored, and comments used for render-target settings are preserved.
- Missing and recursive includes become GLSL `#error` directives at their original location. The compiler rejects active errors and ignores inactive branches and guarded recursion.
- After expansion, JCPP evaluates macros and conditional branches with filesystem access disabled. Inactive declarations are removed before the adapters inspect the source.
- Source size, expansion size, and total include work are bounded.
- Common GLSL 1.20/3.30 spellings such as `texture2D`, `textureSize`, `texcoord`, and `gl_FragColor` are adapted to Vulkan GLSL.
- Unused sampler declarations are removed before validation. Active reads from unsupported samplers still reject the program.

### Options, profiles, and dimensions

- Shader pack `#define` and `const` options, allowed-value validation, profiles with inheritance and program toggles, sliders, nested option screens, pack labels, and screen column settings are supported.
- `program.<name>.enabled` and dimension-qualified `program.world0/<name>.enabled` conditions are evaluated.
- `world0`, `world-1`, and `world1` program overrides are supported, with dimension-aware relative includes. Changing pack or dimension resets temporal matrix and depth history.

### ID mappings

| File | Provides |
| --- | --- |
| `entity.properties` | `entityId` for entity render submissions |
| `block.properties` | Terrain `mat` and `mc_Entity` values; `blockEntityId` for block entity models, items, custom geometry, and sign text |
| `item.properties` | `currentRenderedItemId` for entity, block entity, first-person hand, and shadow paths, resolved from the submitted `ItemStack` (including block items) |

Mappings are read with option preprocessing applied. Terrain selectors support direct block IDs, state predicates (including comma-separated property values), and registered block tags. Direct block and state selectors take priority over tags, as in Iris 1.8+. Unmapped identifiers use `-1`. Terrain sections are rebuilt when a newly selected pack changes the block mappings. Armor-trim and cape special item IDs are not supported.

## Geometry programs

### Program routing and fallbacks

When a specific program is missing, Veyra falls back in the same order as Iris:

| Geometry | Program chain |
| --- | --- |
| Solid terrain | `gbuffers_terrain_solid` → `gbuffers_terrain` |
| Cutout and tripwire terrain | `gbuffers_terrain_cutout` → `gbuffers_terrain` |
| Translucent terrain | `gbuffers_water` → `gbuffers_terrain` |
| Block-breaking overlay | `gbuffers_damagedblock` → `gbuffers_terrain` |
| Entities and armor | `gbuffers_entities` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Translucent entities | `gbuffers_entities_translucent` → `gbuffers_entities` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Spider eyes (`pipeline/eyes`) | `gbuffers_spidereyes` → compatible entity program |
| Translucent first-person hand | `gbuffers_hand_water` → `gbuffers_hand` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Other first-person hand | `gbuffers_hand` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Translucent block entities | `gbuffers_block_translucent` → `gbuffers_block` → `gbuffers_terrain` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Block entities and sign text | `gbuffers_block` → `gbuffers_terrain` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Particles | `gbuffers_particles` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Translucent particles | `gbuffers_particles_translucent` → `gbuffers_particles` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Rain and snow | `gbuffers_weather` → `gbuffers_textured_lit` → `gbuffers_textured` |
| Lightning, dragon rays | `gbuffers_lightning` |
| Leashes | `gbuffers_basic` |
| Lines, block outlines | `gbuffers_line` → `gbuffers_basic` |
| Sky, stars, sunrise | `gbuffers_skybasic` → `gbuffers_basic` |
| Sun, moon, end sky | `gbuffers_skytextured` → `gbuffers_textured` |
| Armor glint | `gbuffers_armor_glint` → `gbuffers_textured` |
| World border | `gbuffers_textured` |
| Beacon beams | `gbuffers_beaconbeam` |
| Clouds | `gbuffers_clouds` |

"Translucent" means a blend-enabled pipeline. Fallback programs are checked against each Vulkan pipeline's available inputs; incompatible entries are skipped and the pipeline and reason are logged.

When a pack uses the solid or cutout terrain variants, Veyra temporarily disables VulkanMod's `uniqueOpaqueLayer` optimization and rebuilds chunks so those programs receive distinct geometry. The previous setting is restored when shaders are disabled or the client closes.

Block entity renderers that submit `core/entity` render types are routed with their texture and draw state preserved. Sign-background quads and renderers using other Minecraft pipeline families are not routed.

### Fragment inputs

Geometry vertex stages are synthesized from VulkanMod's actual vertex formats. A fragment program may only read inputs that the target pipeline can provide.

| Family | Inputs |
| --- | --- |
| Terrain | Atlas (`texture`, `tex`, `gtexture`); vertex color (`color`, `glcolor`, `glColor`, `gl_Color`, raw `glColorRaw`); UV (`texcoord`, `texCoord`, `texCoord0`); lightmap sampler with `lmcoord`, `lmCoord`, or `lightMapCoord`; flat face `normal`; `viewPos`, `vertexPos`, `playerPos`, `dist`; tangent frame; `mat` and `mc_Entity`; per-quad `midCoord`, `signMidCoordPos`, `absMidCoordPos`; BSL `recolor`, `vTexCoord`, `vTexCoordAM` |
| Water | Terrain inputs plus BSL `sunVec`, `upVec`, `eastVec` |
| Entities and hand | Texture (`texture`, `tex`, `gtexture`); `texcoord`; `glcolor` or `color`; `lmcoord`/`lmCoord` with lightmap sampler when UV2 is available; `lightMapColor`; `overlayColor` (also as Iris `entityColor` where available); player-space `normal`; `viewPos` and `viewNormal`; `sphericalVertexDistance`, `cylindricalVertexDistance` |
| First-person hand | Entity inputs plus Iris `isMainHand` (separate main-hand and offhand pipelines) and BSL `sunVec`, `upVec`, `eastVec` |
| Block entities | Entity inputs; Iris lightmap when UV2 is available |
| Sign text | Texture UV and vertex color; lightmap, distances, and `viewPos` when the selected text pipeline provides them |
| Block-breaking overlay | Texture, UV, vertex color, distances, `alphaTestRef` (default 0.1) |
| Particles | Particle atlas, UV, vertex color (already includes lighting), distances |
| Weather | Inputs provided by VulkanMod's rain and snow pipelines |
| Lightning, basic, line | Vertex color and distances; no texture inputs |
| Armor glint | Texture, UV, distances; no vertex color |
| Sky | Color or fog-distance (`gbuffers_skybasic`); UV (`gbuffers_skytextured`) |
| Beacon beams | Texture, UV, vertex color |
| World border | Texture and UV |

Entity and hand `normal` is transformed from the model normal into player space. Terrain `normal` is derived per fragment from camera-relative positions and is the flat geometric face normal. Entity `lmcoord` uses Iris' core-profile scale and offset. `alphaTestRef` maps to the active terrain or entity alpha cutoff where one exists.

Terrain `midCoord` is VulkanMod's compressed per-quad midpoint with the live texture matrix applied. An uninitialized VulkanMod texture matrix is treated as identity.

### Clouds

`gbuffers_clouds.fsh` uses VulkanMod's built-in cloud vertex stage by default. A pack may also provide `gbuffers_clouds.vsh`:

- The vertex stage can use `gl_Vertex`, `gl_Color`, `gl_ModelViewProjectionMatrix`, `gl_ModelViewMatrix` (VulkanMod's `ModelViewMat`), and `ftransform()`, and write `color`, `vertexDistance`, `texcoord`, `dist`, and supported fog fields.
- When the vertex stage requests UVs or normals, Veyra rebuilds the cloud mesh with UV0 and normal attributes. UVs address the source cloud-image pixel for each quad; normals come from each quad's winding.
- BSL `sunVec`, `upVec`, and `eastVec` are bridged.
- Helper functions that are unreachable from `main()` are removed before validation. Reachable helpers that use unsupported inputs are still rejected.

Cloud fragments accept color, distance, `texCoord`, `normal`, and the directional inputs. They can sample a pre-cloud copy of scene color through `colortex0` and its aliases, Minecraft's cloud texture through `texture`, and `noisetex`, using `texture`, `texture2D`, `textureLod`, `texture2DLod`, or `textureSize`. The scene copy requires a transferable Vulkan color image.

### Outputs

Terrain, entity, hand, block entity, and sign text programs can write multiple color attachments in one draw (native MRT). Both explicit `layout(location = N) out vec4` outputs and legacy `gl_FragData` are supported, mapped with `RENDERTARGETS` or `DRAWBUFFERS`. Per-program and per-buffer blend overrides are applied; sparse write masks and per-target blending require the `independentBlend` device feature. Unbound outputs keep private dataflow without allocating attachments.

Other geometry programs, including clouds, write a single output.

### Hand depth

First-person hand programs receive Iris' `MC_HAND_DEPTH` macro with the value `0.125`. Veyra applies the same multiplier to clip-space Z and publishes the matching `gbufferProjection` and inverse while the hand is rendered.

## Shadow programs

Shadow programs render into `shadowtex0`.

| Geometry | Program |
| --- | --- |
| Solid terrain | `shadow_solid` |
| Cutout and tripwire terrain | `shadow_cutout` |
| Translucent terrain | `shadow_water`, only when `shadowTranslucent = true` is set in `shaders.properties` (default `false`) |
| Entities and beacon beams | `shadow_entities` |
| Block entity models, items, custom geometry | `shadow_block` |

Every variant falls back to `shadow.fsh`. If the pack has shadow programs but no `shadow.fsh`, missing variants use a built-in alpha-tested shader. Shadow fragments receive the texture, UV, vertex color, and `alphaTestRef` where applicable.

Veyra reads the GLSL constants `shadowMapResolution`, `shadowDistance`, `shadowIntervalSize`, `sunPathRotation`, `shadowDistanceRenderMul`, and `entityShadowDistanceMul`:

- A positive `shadowDistanceRenderMul` culls terrain sections, entities, and block entities outside that radius during the shadow pass.
- `entityShadowDistanceMul` (default 1.0) scales the entity cull distance relative to the terrain multiplier. 1.0 or a negative value uses the terrain frustum.

`shadowPos` and `shadowCoord` are homogeneous Vulkan clip coordinates. Convert X/Y to UV (including the Y flip) and compare Z directly with the sampled depth.

Entities and block entities render before the pre-translucent depth copy, then translucent terrain renders. This matches Iris' `ShadowRenderer` order.

Sign-background quads and custom shadow vertex stages are not supported.

## Post-processing programs

### Scheduling

| Program | Runs |
| --- | --- |
| `prepare` | After the shadow pass |
| `deferred` | After the opaque depth snapshot, before translucent terrain |
| `composite`, `final` | After world rendering |

Numbered programs run in numeric order. Each pass is a full-screen triangle compiled through VulkanMod. Composite passes also run when the GUI is hidden.

### Color buffers

- `colortex0` is the current scene color. `colortex1`–`colortex15` are allocated on demand as ping-pong buffers. The current buffer side is preserved across the prepare, deferred, and composite boundaries.
- Both sides are cleared every frame by default: `colortex1` to white, the others to transparent black.
- `const bool colortexNClear = false;` keeps a buffer between frames. `const vec4 colortexNClearColor = vec4(r, g, b, a);` sets its clear color (clamped to RGBA8).
- `size.buffer.colortexN` in `shaders.properties` sets a buffer's size. Whole numbers are pixels; decimals are relative to the window (for example `0.5 0.5`). Sizes are limited to VulkanMod's maximum texture size. `colortex0` always matches the scene.
- Formats are set with `const int colortexNFormat = <FORMAT>;`, either as an active declaration or inside a block comment. Supported formats are `R8`, `RGBA8`, `R16F`, `RGBA16F`, and `R32F`. The device must support color-attachment rendering and linear filtering for the chosen format.
- Reduced-size buffers are post-processing targets only; geometry programs do not render into them. `viewWidth` and `viewHeight` always describe the window.

### Multiple outputs

Post programs can declare several `vec4` outputs with `layout(location = N)` or Iris `outColorN` names, mapped with `RENDERTARGETS` or `DRAWBUFFERS`. VulkanMod exposes one color attachment per pass, so Veyra runs one full-screen pass per output. All passes read the same pre-pass buffers.

### Vertex stages

Post programs accept an optional matching `.vsh`. Veyra adapts `gl_VertexID`, `gl_Vertex`, `gl_MultiTexCoord0`, `gl_TexCoord[0]`, and `ftransform()` to its full-screen triangle. Vertex stages can use the same uniforms as post fragments. Custom vertex attributes, legacy matrix attributes, and varyings other than `texcoord`/`texCoord` are not supported.

## Samplers

### Depth

| Sampler | Content | Available in |
| --- | --- | --- |
| `depthtex0`, `DepthSampler` | VulkanMod main depth attachment | Post |
| `depthtex1` | Snapshot after opaque and cutout geometry, before translucent terrain | Post; `gbuffers_water` (nearest/clamp) |
| `depthtex2` | Snapshot after the world, before the first-person hand | Post |

VulkanMod uses zero-to-one clip-space depth: use the sampled `depthtex0` value directly as NDC Z when reconstructing positions. First-frame depth history is initialized with a real transfer clear.

### Auxiliary color in geometry

`gbuffers_water` and entity-family programs can read up to six distinct `colortex1`–`colortex15` buffers, including the legacy aliases `gdepth`, `gnormal`, `composite`, and `gaux1`–`gaux4`. Bindings use the current buffer side after `deferred`. Read-only inputs are allocated and initialized even without a writer.

If a sampled buffer is also an output of the same draw, Veyra ends the render pass and copies the buffer to a separate snapshot before drawing. This is a defined pre-draw snapshot; it does not attempt to reproduce Iris' unsynchronized OpenGL feedback behavior.

### Shadow

| Sampler | Content | Available in |
| --- | --- | --- |
| `shadowtex0` | Completed shadow depth, as `sampler2D` (raw) or `sampler2DShadow` (comparison through `shadow2D` or `texture` with `vec3` coordinates) | Terrain, entity family, post |
| `shadowtex1` | Separate depth copy taken before translucent shadow terrain (nearest/clamp, base level) | Terrain, entity family, post |
| `shadowcolor0`, `shadowcolor` | Completed shadow color (linear/clamp, base level); the aliases share one descriptor | Terrain, entity family, post |

Shadow programs cannot sample their own attachments. `sampler2DShadow` helper parameters in terrain shaders are lowered to Veyra's raw-depth sampler with a software comparison. Post programs can bind at most twelve sampler descriptors in total; unused declarations do not reserve one. Other shadow colors, the context-dependent legacy `shadow` alias, and shadow format and mipmap properties are not supported.

### Noise

`noisetex` is a generated Vulkan texture available to terrain, shadow, entity, hand, block, sign, cloud, and post programs. It is 256×256 by default; `const int noiseTextureResolution` in `shaders.properties` selects a square size from 1 to 4096. It uses linear filtering and repeat addressing.

### Atlas aliases

The legacy `tex` sampler is bridged to VulkanMod's atlas. `textureSize` is allowed on bridged samplers.

### Specular materials

Static resource-pack `_s.png` specular maps are available for terrain and shadow atlases and for entity textures, with straight UNORM channels and linear or LabPBR mipmaps. Missing maps read as zero. Animated specular maps and normal maps are not supported.

## Uniforms

Supported geometry and post programs share one frame snapshot, captured before geometry rendering.

### Built-in scalars

| Group | Uniforms |
| --- | --- |
| Viewport and time | `viewWidth`, `viewHeight`, `frameTimeCounter`, `frameCounter`, and related frame timing |
| World and weather | `worldTime`, `worldDay`, `moonPhase`, `sunAngle`, `shadowFade`, `rainStrength`, `wetness`, `thunderStrength`, `velocity` |
| Player and screen | `eyeAltitude`, `screenBrightness`, `isEyeInWater`, `isSpectator`, `is_sneaking`, `is_sprinting`, `is_on_ground`, `is_hurt`, `is_invisible`, `is_burning` |
| Status effects | `blindness`, `nightVision`, `darknessFactor` |
| Dimension (terrain, post) | `bedrockLevel`, `cloudHeight`, `endFlashIntensity` (0 when Minecraft has no active End flash) |
| Iris common, player, celestial | For example `heavyFog`, `heightLimit`, `currentPlayerHealth`, `sunPosition`, `shadowLightPosition`, `timeAngle`, near/far, held item and light, biome precipitation, sky color, atlas size, lightning bolt position |
| Eye brightness | `eyeBrightness`, `eyeBrightnessSmooth` (`ivec2`, block and sky light on the 0–240 scale); smoothing reads `const float eyeBrightnessHalflife` and defaults to ten ticks |

Boolean uniforms can be declared as `bool` or `int`. `isEyeInWater`, `worldTime`, `worldDay`, `moonPhase`, and `frameCounter` are `int`. `wetness` uses Iris' default 600-tick wetting and 200-tick drying half-lives; pack-specific half-lives are not read.

Pack definitions take precedence over Iris' hardcoded `shadowFade` and `velocity`. Uniforms that Iris never registers keep their initializer or zero; uniforms that Iris does register but Veyra does not support produce an error.

### Matrices and camera

Geometry and post programs accept `gbufferModelView`, `gbufferModelViewInverse`, `gbufferProjection`, `gbufferProjectionInverse`, `gbufferPreviousModelView`, `gbufferPreviousProjection`, `shadowModelView`, `shadowModelViewInverse`, `shadowProjection`, and `shadowProjectionInverse`. G-buffer matrices match the frame used to render geometry; shadow matrices match the active shadow pass. Inverses are computed on the CPU.

`cameraPosition`, `cameraPositionFract`, `previousCameraPosition`, and `previousCameraPositionFract` (`vec3`), plus `cameraPositionInt` and `previousCameraPositionInt` (`ivec3`), are available to terrain, entity-family, cloud, and post programs. The integer and fractional forms preserve precision at large world coordinates.

### Fog

Terrain, entity-family, cloud, and sign text programs accept `fogColor`, `fogStart`, and `fogEnd`, mapped to VulkanMod's live fog buffer. Legacy `gl_Fog.color`, `gl_Fog.start`, `gl_Fog.end`, and the derived `gl_Fog.scale` are bridged through Minecraft's fog uniform buffer. `gl_Fog.density`, `fogDensity`, `fogMode`, and `fogShape` are rejected because no validated runtime value exists.

### Custom uniforms

`uniform.*` and `variable.*` definitions in `shaders.properties` are evaluated for scalar `float`, `int`, and `bool` types, with Iris semantics for `smooth()` (half-life in tenths of a second), floored `fmod`, `edge`, `log(base, x)`, `log10`, `exp10`, `random`, and `randomInt`. Definitions that reference an unregistered `BIOME_*` constant stay 0 with a warning, as in Iris. Vector and other non-scalar custom uniforms are not supported.

## Not implemented

- Custom vertex stages for geometry programs other than clouds. An active non-cloud `gbuffers_*.vsh` whose fragment program exists is rejected, because Veyra would otherwise run VulkanMod's built-in vertex shader in its place.
- Geometry, tessellation, compute, and `begin` stages (rejected with an error).
- Other `gbuffers_*` programs and custom shadow vertex stages.
- MRT for clouds and the remaining single-output geometry programs; genuine simultaneous MRT for post programs.
- `depthtex3` and later, arbitrary Vulkan image formats, custom images and SSBOs.
- Normal maps and animated specular maps.
- Vector custom uniforms and pack-specific vertex attributes.
