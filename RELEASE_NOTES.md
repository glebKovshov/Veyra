# Veyra 0.2.0-beta.34+mc1.21.11 - experimental prerelease

Beta.34 adds static Iris entity normal maps to Veyra's supported shader subset. Entity fragments can read `normals` from the resource-pack `_n.png` paired with a resource-backed Minecraft `SimpleTexture` albedo image. A dedicated Vulkan graphics fixture verifies the authored map reaches the cow's entity sampler.

## Installation

- Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, VulkanMod 0.6.8, and Java 21 or newer.
- Install `veyra-0.2.0-beta.34+mc1.21.11.jar` beside VulkanMod. Keep only one Veyra JAR in the instance and do not install the sources JAR.
- Put shaderpack folders or ZIPs in `shaderpacks`. Press F6 to select a pack and Configure / Apply to edit supported options and profiles.

## Validation and known limits

The two static entity normal-map fixtures passed on an NVIDIA RTX 3070. With a matching 64×64 authored cow map, the latest clean run had **18,648 magenta pixels / 0 after disabling**. With `_n.png` absent, the active frame had **18,410 red fallback pixels / 0 after disabling**. Terrain retained its separate green signal. These synthetic fixtures verify authored lookup, the missing-map fallback, upload, and Vulkan descriptor binding; they do not verify full shaderpack visuals.

The local JDK 21 release gate passed **544 tests** with no failures or skips, validated **101 example shaders** and compiled **117 stages to Vulkan SPIR-V**, built the mod, packaged the example packs, and compiled graphics-smoke sources. The candidate JAR passed `scripts/verify_jar.py`. Hosted Build, source audit, and Lavapipe graphics checks run on the release candidate before publication.

Only static maps paired with resource-backed `SimpleTexture` images are covered. Downloaded player skins and animated normal/specular maps remain unsupported, as do many Iris features. Veyra remains an experimental compatibility subset; full Iris parity and stable-release readiness are not claimed. See [COMPATIBILITY.md](COMPATIBILITY.md) for measured results and limitations.
