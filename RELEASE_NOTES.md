# Veyra 0.2.0-beta.11 — experimental prerelease

This prerelease adds limited per-item `currentRenderedItemId` support. It is not a stable release and does not claim full Iris parity. Third-party shaderpacks still have active source-level incompatibilities and have not been certified as playable.

## Installation

- Minecraft **1.21.11**, Fabric Loader **0.18.4+**, VulkanMod **0.6.8**, Java **21+**.
- Install `veyra-0.2.0-beta.11+mc1.21.11.jar` in the instance's `mods/` folder alongside VulkanMod. Do not install the `-sources.jar`. VulkanMod bundles its required Fabric modules; the development build uses Fabric API 0.140.0+1.21.11.
- Optional example packs can be extracted into `shaderpacks/`. Press **F6**, select a pack, then use **Configure / Apply** for options and profiles. Overrides persist under `veyra/options/`; archives are not modified.

## Changes since beta.10

- Bridge `currentRenderedItemId` from the source `ItemStack` for item, armor, hand, and entity-shadow submissions. Resolve the current pack's `item.properties` map at submit time, including block items, so changing packs does not retain stale IDs.
- Add the `current-rendered-item-id` example and a real-client Lavapipe fixture that renders diamond and stone item entities with different IDs and requires separate magenta and green readback signals.
- Keep Iris hardcoded armor-trim and cape IDs unsupported. Unmapped item stacks use `-1`.

## Verification

- Local JDK 21.0.12.1 `test validateShaders build packageExamplePacks compileGraphicsSmokeJava --rerun-tasks`: **203 tests**, zero failures/errors/skips; 47 example shader sources and 44 direct Vulkan SPIR-V compilations. The graphics-smoke source set compiles.
- The pinned source audit covers 4 packs, 12 defaults, 63 declared profiles, and 5,178 preprocessed stages; 126 geometry fragments are accepted and 591 rejected. This is a source-only check.
- GitHub Build [39358133674](https://github.com/glebKovshov/Veyra/actions/runs/39358133674), source audit [39358133689](https://github.com/glebKovshov/Veyra/actions/runs/39358133689), and all 16 Lavapipe graphics fixtures [39358133656](https://github.com/glebKovshov/Veyra/actions/runs/39358133656) passed. The item fixture measured 32,321 active magenta pixels for ID 42, 14,102 active green pixels for ID 43, 0 disabled green pixels, and 0 runtime Vulkan validation errors.
- Release workflow [39358811325](https://github.com/glebKovshov/Veyra/actions/runs/39358811325) passed the pinned source audit, all 16 graphics fixtures, build/tests, and distributable verification. The downloaded release JAR is 577,433 bytes; SHA-256 `34482621D4D1896E5151130F64300397A823BEEC95535D836CDD72CC2827BC34`, matching `SHA256SUMS.txt` and `scripts/verify_jar.py`.
- Shutdown reported 10,036 `VUID-vkDestroyDevice-device-05137` diagnostics, separate from the zero runtime validation errors. This is not a clean-teardown claim. The synthetic item fixture does not establish third-party pack appearance or physical-GPU compatibility.

## Known limits

- Complementary Reimagined/Unbound water still needs `viewVector`; entity/block/hand programs need `entityColor`; selected profiles require compute execution.
- BSL water needs `mat`, entity programs need `entityColor`, hand needs `isMainHand`, block inputs and cloud `texture` sampling are incomplete.
- Sildur's Vibrant uses unsupported `colortex4` geometry reads, water attachment feedback, and unsupported cloud inputs.
- Armor trim/cape item IDs, normal maps, animated material maps, broader sampler/property behavior, arbitrary geometry vertex inputs, non-terrain MRT, compute/tessellation execution, physical-GPU checks, and third-party pack visual validation remain incomplete.

Unsupported active features produce diagnostics instead of fake output. See [COMPATIBILITY.md](COMPATIBILITY.md), [VALIDATION.md](VALIDATION.md), and [CHANGELOG.md](CHANGELOG.md) for details and evidence.
