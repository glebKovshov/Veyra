# Veyra

Veyra is a client-side shader mod for Minecraft 1.21.11 on Fabric with VulkanMod 0.6.8. It runs a supported subset of GLSL post-processing passes through VulkanMod's shader compiler and Minecraft's frame graph.

## Install and use

1. Install Fabric Loader 0.18.4 or newer and VulkanMod 0.6.8 for Minecraft 1.21.11.
2. Put the Veyra jar in the instance's `mods` folder.
3. Put a shader pack folder or zip in `shaderpacks`.
4. Press F6 in game and select a pack.

Veyra accepts folders and zip archives that contain one or more of these fragment programs under `shaders/`: `prepare.fsh` through `prepare99.fsh`, `deferred.fsh` through `deferred99.fsh`, `composite.fsh` through `composite99.fsh`, and `final.fsh`. A single wrapper directory inside a zip is also detected. The selected pack is saved to `veyra/selected-pack.txt` in the game directory. The `examples/grayscale` folder contains a pass-through composite pass followed by a grayscale final pass.

## Supported shader subset

The renderer applies supported fragment programs in Iris order after the world render: prepare, deferred, composite, then final. Numbered programs run in numeric order. Each pass runs as a full-screen triangle through VulkanMod's shader compiler and reads the current color as `colortex0`. Common GLSL 1.20/3.30 spellings such as `texture2D`, `texcoord`, and `gl_FragColor` are adapted to Vulkan GLSL. Pipeline identities include a hash of the adapted source so changing packs compiles a distinct Vulkan pipeline instead of reusing the previous shader.

This is still a compatibility layer in progress, not a general Iris or OptiFine shaderpack port. World geometry programs (`gbuffers_*`), begin and shadow passes, custom vertex or geometry programs, shadow maps, depth sampling (`depthtex0`), `depthtex1` and later targets, `colortex1` and later targets, multiple fragment outputs, compute shaders, and pack render-target directives are not implemented. Unsupported color and depth targets are rejected with an explanatory message when Veyra compiles them. Veyra lists packs with supported post passes and disables the effect if Vulkan compilation or rendering fails.

## Build

Requirements: Java 21 and the included Gradle wrapper.

```powershell
.\gradlew.bat build
```

The jar is written to `build/libs/`. GitHub Actions builds on pushes and pull requests and uploads the jar as a workflow artifact.

## License

MIT. See [LICENSE](LICENSE).
