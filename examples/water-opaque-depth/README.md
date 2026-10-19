# Opaque depth in translucent terrain

Place this folder in `shaderpacks/` and select it with F6. Use a scene with water above an opaque floor. A magenta water surface means `gbuffers_water` read copied opaque depth behind its own fragment; green means the depth predicate did not pass. The signal travels through terrain MRT target 1 and the final pass.

This is an original diagnostic fixture, not a general water shader. It is used by the software Vulkan integration matrix. `depthtex1` is supported here only in `gbuffers_water`; opaque and shadow terrain do not have this binding.
