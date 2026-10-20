# Veyra 0.2.0-beta.24+mc1.21.11 — experimental prerelease

Beta.24 adds limited custom vertex shader execution for supported terrain programs after VulkanMod's terrain setup. It is an experimental subset; it does not provide full Iris compatibility or stable-release support.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, Java 21 or newer.
- Install `veyra-0.2.0-beta.24+mc1.21.11.jar` in the instance's `mods` folder alongside VulkanMod. Do not install the sources JAR.
- Optional example packs can be extracted into `shaderpacks`. Press F6, select a pack, then use Configure / Apply for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.23

- Run supported `gbuffers_terrain`, `_solid`, `_cutout`, and `_water` custom vertex programs after VulkanMod's terrain setup.
- Bridge the available terrain position, color, normal, lightmap, material, midpoint UV, tangent, chunk-fade, and `noisetex` inputs.
- Link supported scalar, vector, and matrix vertex outputs to matching fragment inputs with checked types and interpolation. Unused VulkanMod outputs are pruned so terrain pack varyings can use available locations.
- Store tangent components in scalar vertex attributes supported by VulkanMod 0.6.8; preserve the packed terrain stride and field offsets.
- Reject unsupported attributes, samplers, and varying interfaces with named diagnostics.

PR [#59](https://github.com/glebKovshov/Veyra/pull/59) merged into `main` as `d97dcb135dc4d289ed149b9916dc932b9f6d814a`. The final PR head passed Build [39464726776](https://github.com/glebKovshov/Veyra/actions/runs/39464726776), pinned source audit [39464726807](https://github.com/glebKovshov/Veyra/actions/runs/39464726807), and all **24/24** Lavapipe fixtures [39464726902](https://github.com/glebKovshov/Veyra/actions/runs/39464726902). The terrain VSH-to-FSH readback measured 223,366 active magenta pixels, 0 with shaders disabled, and 0 runtime Vulkan validation errors. Process teardown still reports 10,031 diagnostics.

## Shaderpack audit and known limits

The pinned audit covers 4 shaderpack archives, 75 default/profile/dimension cases, and 5,178 shader stages. It accepted 705 and rejected 12 geometry fragments. Every case still encounters the unsupported non-terrain `gbuffers_armor_glint.vsh`; every record has `visualValidation=false`. The 12 fragment rejections are Sildur `LightC` inputs. The selected Complementary profiles also use unsupported `shadowcomp.csh` compute programs.

No complete third-party shaderpack has been visually validated, and physical-GPU testing has not been completed. Entity and armor-glint custom vertex programs, compute execution, other pack-specific inputs, and Vulkan process teardown ownership remain open work. Beta.24 is experimental and makes no full Iris-parity claim.

The tag-triggered Release workflow rebuilds and tests the versioned artifact, reruns the pinned audit and graphics fixtures, verifies distributable contents, and publishes the installable JAR, sources JAR, example-pack archive, and SHA-256 manifest.
