# Veyra

Veyra is a client-side shader mod for Minecraft 1.21.11 on Fabric. It runs a supported subset of Iris shader pack programs on [VulkanMod](https://github.com/xCollateral/VulkanMod).

> [!WARNING]
> The 0.2 line is an **experimental prerelease**. It does not provide full Iris compatibility, and no third-party shader pack is certified as playable. See [Compatibility](docs/compatibility.md) for measured results.

## Status

| | |
| --- | --- |
| Latest prerelease | [0.2.0-beta.23](https://github.com/glebKovshov/Veyra/releases/tag/v0.2.0-beta.23%2Bmc1.21.11) |
| Latest stable | [0.1.1](https://github.com/glebKovshov/Veyra/releases/tag/v0.1.1%2Bmc1.21.11) |
| Minecraft | 1.21.11 |
| Loader | Fabric Loader 0.18.4 or newer |
| Renderer | VulkanMod 0.6.8 (exact version required) |
| Java | 21 or newer |

Popular packs (Complementary, BSL, Sildur's Vibrant) do not load yet: their custom geometry vertex stages are not executed and are rejected with an explicit error. The main remaining gaps are listed in [Architecture → Known gaps](docs/architecture.md#known-gaps).

## Installation

1. Install Fabric Loader and VulkanMod 0.6.8 for Minecraft 1.21.11. The VulkanMod JAR already bundles the Fabric API modules and Vulkan libraries it needs.
2. Download `veyra-<version>.jar` from the [releases page](https://github.com/glebKovshov/Veyra/releases) and put it in the instance's `mods` folder. Do not install the `-sources.jar`.
3. Put a shader pack folder or ZIP archive in `shaderpacks`. A single wrapper directory inside a ZIP is detected automatically.
4. Start the game and press **F6** to select a pack.

VulkanMod is hooked through internal classes, so other VulkanMod versions need explicit compatibility testing.

## Usage

- **F6** opens the shader pack menu. The selection is saved to `veyra/selected-pack.txt`.
- **Configure** opens the pack's options and profiles, including sliders and nested screens. **Apply** recompiles the pack before saving, so a failed change keeps the previous pack active.
- Per-pack option overrides are saved under `veyra/options/`. Shader pack archives and sources are never modified.
- **Refresh list** rescans `shaderpacks` and recompiles the active pack from disk. A failed compilation keeps the previous pipelines; removing or breaking the active pack disables shaders.
- **Disable shaders** turns shaders off for the session, even if the selection cannot be saved.

Unsupported programs, inputs, and stages are reported in the game log with the program name and reason. The known exceptions are listed in [Shader support → Not implemented](docs/shader-support.md#not-implemented).

## Example packs

The repository contains small original shader packs that each exercise one feature. Build them with `./gradlew packageExamplePacks` and extract `build/distributions/veyra-example-packs-*.zip` into `shaderpacks`; the same archive is attached to every release. Start with `grayscale` or `animated`. See [Example packs](docs/examples.md) for the full list.

## Building

Requirements: JDK 21. The Gradle wrapper is included.

```sh
./gradlew build                 # compile, run unit tests, remap the mod
./gradlew validateShaders       # compile example terrain, entity, hand, and cloud shaders with shaderc
./gradlew packageExamplePacks   # package examples/ into a ZIP
```

On Windows, use `gradlew.bat`. The mod JAR is written to `build/libs/`.

## Documentation

| Document | Contents |
| --- | --- |
| [Shader support](docs/shader-support.md) | Supported programs, inputs, uniforms, samplers, and pack properties |
| [Compatibility](docs/compatibility.md) | Current results for pinned third-party packs and how to reproduce them |
| [Architecture](docs/architecture.md) | How Veyra maps Iris concepts onto VulkanMod, and the known gaps |
| [Validation](docs/validation.md) | Automated checks, what they prove, and what is still unverified |
| [Example packs](docs/examples.md) | Catalog of the bundled example and fixture packs |
| [Changelog](CHANGELOG.md) | Release history |
| [Contributing](CONTRIBUTING.md) | Development workflow, documentation rules, and release process |

## License

Veyra is released under the [MIT License](LICENSE). Bundled third-party components are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Iris and VulkanMod source code was studied but not copied. Third-party shader packs are not redistributed.
