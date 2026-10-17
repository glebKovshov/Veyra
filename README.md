# Veyra

Veyra is a client-side shader mod for Minecraft 1.21.11 on Fabric with VulkanMod 0.6.8. It sends a supported GLSL final pass through VulkanMod's shader compiler and Minecraft's post-processing frame graph.

## Install and use

1. Install Fabric Loader 0.18.4 or newer and VulkanMod 0.6.8 for Minecraft 1.21.11.
2. Put the Veyra jar in the instance's `mods` folder.
3. Put a shader pack folder or zip in `shaderpacks`.
4. Press F6 in game and select a pack.

Veyra currently accepts packs containing `shaders/final.fsh` at the archive or folder root. A single wrapper directory inside a zip is also detected. The selected pack is saved to `veyra/selected-pack.txt` in the game directory. The `examples/grayscale` folder is a small pack you can copy into `shaderpacks` to try the supported path.

## Supported shader subset

The current renderer runs one final full-screen pass with the game's color buffer bound as `colortex0` and copies the result back to the main target. It adapts common GLSL 1.20/3.30 spellings used by simple final shaders, including `texture2D`, `texcoord`, and `gl_FragColor`, to Vulkan GLSL.

This is an early compatibility layer, not a general Iris or OptiFine shaderpack port. Terrain and entity programs, shadows, depth buffers, multiple color attachments, compute shaders, and multi-pass composite chains are not supported. A pack that depends on those features may fail compilation or produce a different result. Veyra displays unsupported packs and disables the pass if Vulkan compilation or rendering fails.

## Build

Requirements: Java 21 and the included Gradle wrapper.

```powershell
.\gradlew.bat build
```

The jar is written to `build/libs/`. GitHub Actions builds on pushes and pull requests and uploads the jar as a workflow artifact.

## License

MIT. See [LICENSE](LICENSE).
