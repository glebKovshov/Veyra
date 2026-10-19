# Veyra 0.2.0-alpha.1 — experimental supported subset

This is an **alpha prerelease**, not stable Iris parity. The stable 0.1.1 release remains available. Complementary, BSL and Sildur's have known source incompatibilities and are not certified playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Put `veyra-0.2.0-alpha.1+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the sources JAR. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Extract the optional example-pack archive into `shaderpacks/`, or add a supported pack folder/ZIP. Press **F6**, select the pack and use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes

- Preserves the existing VulkanMod bridges and adds persistent options/profiles, conditional preprocessing, dimension-specific programs, separate prepare/deferred/composite boundaries, terrain MRT, initialized depth snapshots and generated noise textures.
- Bridges supported Iris uniforms and entity shadow depth sampling, with device feature checks and frame-fence resource retirement.
- Bounds recursive include expansion work and fixes Windows full-matrix audit argument handling.
- Release publication requires the pinned source audit, Lavapipe synthetic rendering, Gradle build/tests, shaderc validation and JAR-content verification. See CHANGELOG.md for the full implementation history.

## Evidence and limitations

Local JDK 21 validation: **92 JUnit tests**, no failures/errors/skips; **21** example sources preprocessed, **18** direct Vulkan SPIR-V compilations, conditional-include checks and the entity shadow sampler fixture pass. The remapped JAR excludes the graphics test harness and includes required metadata, mixins and the licensed nested JCPP dependency.

Pinned source audit: **75** configurations, **5,178** sources preprocessed, **18** geometry fragment/configuration cases accepted and **699** rejected. This measures adapter coverage, not successful in-game rendering.

| Pack | Source audit | Visual compatibility |
| --- | --- | --- |
| Complementary Reimagined r5.9.3 | 0 accepted / 270 rejected | Not verified; known unsupported resources/stages |
| Complementary Unbound r5.9.3 | 0 / 270 | Not verified; known unsupported resources/stages |
| BSL 10.1.8 | 18 / 144 | Not verified; incomplete pipeline |
| Sildur's Vibrant 2.02 Extreme | 0 / 15 | Not verified; unsupported geometry samplers |

The release workflow runs the small Lavapipe terrain-MRT/composite readback fixture. It does not render those packs. Historical runs have no runtime validation errors but report **10,031** Vulkan child-object diagnostics during whole-process teardown; clean teardown and memory stability are not established. Physical GPU/UI testing is currently blocked by the Windows Computer Use native pipe; resize/reload/world-switch stress and FPS benchmarks remain unverified.

Arbitrary geometry vertex stages, non-terrain MRT, PBR `specular`, a separate opaque `shadowtex1`, additional geometry samplers/custom uniforms and compute/tessellation execution remain incomplete. Unsupported active features produce diagnostics rather than fake output. See COMPATIBILITY.md, ARCHITECTURE.md and VALIDATION.md. Third-party shaderpacks are not redistributed.
