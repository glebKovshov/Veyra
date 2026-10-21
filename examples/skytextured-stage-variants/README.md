# Sky-textured render-stage fixture

This synthetic shaderpack colors the sun magenta and the moon green using Iris `renderStage` constants. The `skytextured-stage-variants` graphics smoke checks both values in the live Vulkan renderer, then disables Veyra and confirms that both signals disappear. It tests per-draw stage selection for Minecraft's shared celestial pipeline; it does not measure third-party shaderpack compatibility.
