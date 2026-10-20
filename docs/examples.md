# Example packs

Every directory under [`examples/`](../examples) is a small original shader pack that exercises one feature. They are not meant to look good; most display a solid diagnostic color when the feature works.

Build all examples with `./gradlew packageExamplePacks` and extract `build/distributions/veyra-example-packs-*.zip` into `shaderpacks`. The same archive is attached to every release. `./gradlew validateShaders` compiles the examples' terrain, water, entity, hand, and cloud fragment programs with shaderc; the other example programs are only checked at runtime or by hand.

Packs marked **CI** are also run in a real Minecraft client on software Vulkan (Lavapipe) by the [Graphics smoke](../.github/workflows/graphics-smoke.yml) workflow, which reads back the frame and checks the expected colors. See [Validation](validation.md).

## Post-processing

| Pack | Demonstrates |
| --- | --- |
| `grayscale` | Two-pass composite chain; a good first test |
| `animated` | `frameTimeCounter` and a shared include from `shaders/lib/` |
| `composite-vertex` | Post vertex stage driving an animated crop |
| `explicit-outputs` | `layout(location)` and `outColorN` outputs |
| `colortex1` | Writing a second target and sampling it later |
| `colortex2` | Writing `colortex2` and `colortex7` in one pass |
| `colortex-persistence` | Keeping and accumulating a buffer between frames |
| `colortex-format-size` | Half-resolution `RGBA16F` buffer with tone mapping |
| `depthtex0` | Main depth attachment |
| `depthtex1` | Main versus opaque-only depth |
| `depthtex2` | Main, opaque-only, and pre-hand depth |
| `matrix-depth` | Depth reconstruction with matrix and camera uniforms |
| `world-weather` | World and weather uniforms |
| `player-state` | Player state uniforms |
| `player-effects` | Status-effect uniforms |
| `custom-uniforms` | `shaders.properties` `uniform.*`/`variable.*` expressions, `velocity` — **CI** |

## Terrain

| Pack | Demonstrates |
| --- | --- |
| `gbuffers-terrain` | Base, solid, cutout, and water programs with distinct tints |
| `gbuffers-damagedblock` | Block-breaking overlay |
| `geometry-tex-sampler` | Legacy `tex` atlas alias and `textureSize` |
| `geometry-mrt` | Native terrain MRT — **CI** |
| `geometry-mid-texcoord` | Per-quad `midCoord`, `signMidCoordPos`, `absMidCoordPos` — **CI** |
| `terrain-block-id` | `block.properties` terrain IDs — **CI** |
| `water-block-id` | Fluid `mc_Entity` marker — **CI** |
| `terrain-uniforms` | Terrain and shadow uniform buffers |
| `terrain-shadow-helper` | `sampler2DShadow` helper lowering |
| `water-opaque-depth` | `depthtex1` in `gbuffers_water` — **CI** |
| `water-color-inputs` | Auxiliary `colortex` inputs in water, including ping-pong and feedback — **CI** |

## Entities, hand, and block entities

| Pack | Demonstrates |
| --- | --- |
| `gbuffers-entities` | Entity texture, vertex color, light, and overlay |
| `gbuffers-spidereyes` | Dedicated eye pipeline |
| `gbuffers-hand` | Opaque first-person hand |
| `gbuffers-hand-water` | Translucent first-person hand |
| `gbuffers-hand-depth` | `MC_HAND_DEPTH` macro |
| `gbuffers-block-entities` | Opaque and translucent block entities |
| `gbuffers-block-text` | Sign text |
| `entity-mrt` | Entity-family MRT through `colortex1` — **CI** |
| `entity-colortex-feedback` | Entity sampling a buffer it also writes — **CI** |
| `current-rendered-item-id` | `item.properties` and `currentRenderedItemId` — **CI** |

## Shared geometry inputs

| Pack | Demonstrates |
| --- | --- |
| `geometry-normals` | Terrain and entity normals |
| `geometry-lightmap` | Entity and terrain lightmap coordinates |
| `geometry-view-space` | Terrain and entity `viewPos`, `viewNormal`, `dist` — **CI** |
| `geometry-fog` | Fog uniforms and distances in terrain, entities, and block entities |
| `geometry-uniforms` | Scalar uniforms in entity, cloud, and entity-shadow programs |
| `geometry-camera-position` | Camera position uniforms |
| `geometry-eye-brightness` | `eyeBrightness` |

## Other geometry

| Pack | Demonstrates |
| --- | --- |
| `gbuffers-particles` | Opaque and translucent particles |
| `gbuffers-weather` | Rain and snow |
| `gbuffers-textured-lit` | Shared entity, particle, and weather fallback |
| `gbuffers-textured` | Shared textured fallback |
| `gbuffers-lightning` | Color-only lightning |
| `gbuffers-basic-lines` | Leashes and lines |
| `gbuffers-armor-glint` | Armor glint |
| `gbuffers-sky` | Basic and textured sky |
| `gbuffers-beaconbeam` | Beacon beam |
| `gbuffers-clouds` | Color-only cloud fragment |
| `cloud-colortex0` | Scene-color copy and `noisetex` in clouds — **CI** |
| `cloud-texture-inputs` | Cloud texture, UVs, normals, and directional inputs — **CI** |
| `cloud-model-view` | Cloud vertex `gl_ModelViewMatrix`, `texcoord`, `dist` — **CI** |

## Shadows and materials

| Pack | Demonstrates |
| --- | --- |
| `shadow` | All shadow program variants, `shadowDistanceRenderMul`, `entityShadowDistanceMul` |
| `shadowtex0` | Shadow comparison sampling in a post pass |
| `shadow-color-terrain` | `shadowcolor0` in terrain — **CI** |
| `shadow-color-entity` | `shadowcolor0` in entities — **CI** |
| `water-shadow-depth-terrain` | Distinct `shadowtex0`/`shadowtex1` in terrain — **CI** |
| `water-shadow-depth-entity` | Distinct `shadowtex0`/`shadowtex1` in entities — **CI** |
| `water-post-shadow-inputs` | Shadow inputs across prepare, deferred, composite, and final — **CI** |
| `specular-atlas` | Atlas `_s.png` maps; needs a matching resource pack — **CI** |
| `specular-entity` | Entity `_s.png` maps — **CI** |

Some packs have their own `README.md` with setup details.
