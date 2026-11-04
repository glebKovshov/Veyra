# Iris reference capture

Standalone validation client for Minecraft 1.21.11, Iris 1.10.8 and Sodium 0.8.14.
It reproduces GraphicsSmoke's material wall or natural terrain scene and captures
`iris-reference-initial.png`. It is excluded from Veyra's production JAR.

Use JDK 21 and Gradle 9.4.0 for this project (Loom 1.16.3); the root Veyra wrapper
uses Gradle 9.2.1 and cannot run it. No third-party shaderpack is redistributed.
Fetch and verify pinned archives with the root `scripts/run_shaderpack_source_audit.py`.

From the repository root, prepare a **new** run directory for each renderer:

```powershell
python scripts/prepare_graphics_smoke.py --fixture third-party-natural-world --root build/validation-runs/iris-bsl-natural --shaderpack-archive build/shaderpack-source-audit/inputs/BSL_v10.1.8.zip --shaderpack-name bsl --profile HIGH
New-Item -ItemType Directory build/validation-runs/iris-bsl-natural/config
@'
enableShaders=true
shaderPack=bsl
disableUpdateMessage=true
'@ | Set-Content build/validation-runs/iris-bsl-natural/config/iris.properties
```

BSL's unchanged defaults select HIGH; require Iris's log to confirm
`Profile: HIGH (+0 options changed by user)`. `veyra/shaderpack-profile.txt` controls
Veyra only. For other profiles, resolve inherited profile options and save their
values in Iris's `shaderpacks/<pack-name>.txt`; verify the resulting Iris profile
and option changes rather than assuming that Veyra's marker configures Iris.

For a fresh single-pack Veyra run, its `veyra/options/*.properties` file contains
the actual non-default overrides. Copy that file to the Iris pack's adjacent
`shaderpacks/<pack-name>.txt` before starting Iris. An empty override set is
correct only when the requested profile already matches the source defaults.
For Unbound COMPLEMENTARY, require `Profile: COMPLEMENTARY (+0 options changed by user)`;
an Iris `Profile: HIGH` frame must be excluded even when all shader files match.

Run the installed Gradle 9.4.0 executable (replace `gradle` below with its path):

```powershell
gradle --no-daemon -p validation/iris-reference classes
gradle --no-daemon -p validation/iris-reference runClient '-PreferenceRunDir=../../build/validation-runs/iris-bsl-natural' '-PreferenceOutput=../../build/reports/iris-bsl-natural'
```

Loom's run directory must be relative to this standalone project on Windows.
For the material wall, prepare `third-party-shaderpack` instead. Both scenes use
seed 1, daylight 1000 and a fixed camera. Natural terrain uses the generated
surface at (0,0), flying at surface+2, pitch 15 and yaw 135; the material wall
uses (0.5,71,0.5), pitch -2 and yaw 0 after the player lands. The capture log
records the actual position, rotation, time and floor block; compare these with
Veyra's scene evidence before comparing images. Keep separate fresh worlds so
one renderer's saves cannot alter the other's initial state.

For an animation-phase comparison, add `-PgraphicsSmokeFixedClock` to Veyra's
`runGraphicsSmoke` command and `-PreferenceFixedClock` to this client's command.
Both test-only harnesses then use frameTimeCounter=8 seconds, frameTime=1/30
second and frameCounter=160. Require the `REFERENCE_CLOCK` lines from both
clients, including Iris's separate frame-counter line. This also fixes TAA's
jitter phase; retain normal-clock lifecycle runs as independent evidence for
continuous animation and resource stability. The production JAR has no clock
override.

All reference launches use OpenAL's `null` backend, master/category volume zero,
and a test-only mixin that cancels mouse capture. The log must show `No Output`
and `REFERENCE_INPUT mouseGrabbed=false cursorMode=212993 ... masterVolume=0 ...
silentAudioBackend=true` (decimal separators depend on the locale).

Archive hashes, shader-tree hashes, selected profile/options, client logs and
captures are evidence to retain with the comparison. This runner does not claim
pixel identity: animation timing, render backend and renderer settings can affect
the frame. It captures a controlled initial scene, not an Iris lifecycle test.

Both harnesses also save `frame-matrices.json` at the initial capture. Matrices
are column-major arrays. Model-view and shader-pack shadow matrices can be
compared directly; Veyra's raw gbuffer projection retains the native depth
convention and must be converted before comparing it to Iris's OpenGL projection.
Both harnesses save `frame-environment.json` with camera and dimension cloud
heights. These may differ in biomes or datapacks that override the camera attribute;
the shader uniform follows the camera, as in Iris 1.10.8.

For a depth-isolation diagnostic only, add `-PreferenceNativeDepth`. This changes
the actual camera/GPU projection and captured Iris matrix to Veyra's near-infinite
depth coefficients in OpenGL coordinates. Require `REFERENCE_DEPTH nativeDepth=true`
and verify the captured matrix (m22=-1.000000238418579, m32=-0.0999999). Leave this
option off for the unmodified Iris baseline; label its captures as diagnostics.
