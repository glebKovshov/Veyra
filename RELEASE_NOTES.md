# Veyra 1.1.0

Veyra 1.1.0 runs Photon v1.4, fixes shadow, water and post-processing compatibility issues shared by other packs, and adds an Iris-style shader menu to VulkanMod's video settings.

## Changes since 1.0.0

- **Photon v1.4.** Photon now renders end to end. This needed:
  - raw 3D textures loaded from `.dat` files;
  - Iris core-profile inputs in leash and line programs;
  - entity tangents and struct varyings;
  - the `RG16F` color format;
  - several Vulkan GLSL portability fixes: the `sampler` identifier, multi-line layouts, narrow fragment outputs and float depth-compare results.

  In a matched foliage scene, the frame differs from Iris 1.10.8 by about 6/255 RGB.
- **Post-processing.** Post vertex stages receive `gl_Vertex` as the [0,1] quad Iris uses, in OpenGL clip space. Packs that scale it into tiles, as Photon does for bloom, no longer smear across the frame.
- **Iris compatibility.**
  - `flip.<program>.<buffer>` directives are honored.
  - Custom-uniform expressions can read matrix elements as `matrix.column.row`. Photon uses this to compute its sun and light directions.
  - The fixed-function alpha test is applied only to `gl_FragColor`/`gl_FragData[0]` outputs, as Iris does. Packs that pack data into alpha (such as Photon's light levels) keep it.
- **VulkanMod GLSL workaround.** VulkanMod's parser treated `in`/`out` function parameters as stage interfaces, so out-parameters returned nothing. In Photon this made ambient occlusion flicker on foliage.
- **Shadows.**
  - Depth for deferred programs is copied before translucents.
  - Shadow culling covers the full drawn depth range and does not use camera occlusion.
  - Post programs read the current frame's shadow matrices.

  Together these fix flicker, short shadow reach and camera-dependent casters.
- **Water.**
  - Vertex stages see the sampling projection, so screen-space reflections start from the right place.
  - Tangent handedness matches Iris.
  - Waterlogged plants keep the water material.
- **Loading.** Packs load about twice as fast; on an RTX 3070, Photon loads from the menu in about 12 s instead of 21 s. The shader screens show a loading status rather than freezing on the click.
- **Menu.** **Options → Video Settings → Shader Settings** opens a full-screen, Iris-style pack list (F6 still works). The list has:
  - a shaders on/off toggle and Active/Unsupported markers;
  - Open Shader Pack Folder, Refresh and Shader Pack Settings buttons;
  - Apply, Cancel and Done.

  Clicking a pack only selects it; Apply, Done or a double-click loads it. Option screens use the whole window. English and Russian translations are included.

## Installation

Requires Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8 and Java 21 or newer.

Assets: the install JAR `veyra-1.1.0+mc1.21.11.jar`, a sources JAR, an example-packs ZIP and `SHA256SUMS.txt`. Put the install JAR beside VulkanMod and remove older Veyra JARs. Open **Options → Video Settings → Shader Settings** (or press F6) to select and configure a shader pack. Veyra does not redistribute or modify shader pack archives.

## Validation

- JDK 21 checks: 693 JUnit tests pass, 173 shader compilation checks pass, and the 75-case pinned source audit (4 packs × 3 dimensions, 939 geometry programs, 18 active compute stages) matches the 1.0.0 baseline.
- RTX 3070, compared with 1.0.0 frames:
  - Complementary Reimagined HIGH, BSL and Sildur render unchanged.
  - All four packs, Photon included, load and render through the menu path.
  - 55 of the 56 graphics fixtures pass. `shadow-color-entity` fails on this machine with 1.0.0 as well; it passes on the hosted Lavapipe gate. `entity-vertex-execution` failed once while the window was still 10×10 at startup and passed on two reruns.
- Photon was compared with Iris 1.10.8 on matched scenes with a fixed clock:
  - Frame-to-frame foliage change is 0.06/255 in Veyra and 0.12/255 in Iris.
  - The ambient occlusion buffer, sun direction and post-pass geometry match Iris.
- Hosted gates: the Lavapipe graphics matrix, the source audit, the build and shader compilation. The publication workflow downloads its own assets and checks them against `SHA256SUMS.txt` and the JAR metadata.

## Known limits

- Pixel parity with Iris is not certified. In Photon, magma emission is dimmer than in Iris.
- After a pack loads or the world appears, Photon's clouds take about 16 frames to fill in through their temporal 4×4 reconstruction, as they do in Iris. At low frame rates the squares stay visible longer; lowering `CLOUDS_TEMPORAL_UPSCALING` shortens this.
- A pack switch compiles on the render thread: Photon takes about 12 s and Complementary about 20 s on an RTX 3070.
- Up to 16 logical color buffers (Iris allows 32) and 8 simultaneous outputs. Geometry/tessellation stages, matrix-typed and integer-vector custom uniforms, Photon's LPV/colored lights and 1D/RECTANGLE raw textures are not supported.
- Hardware validation covers an RTX 3070 and Lavapipe. Other GPUs, packs and profiles are untested.

See COMPATIBILITY.md for the full contract.
