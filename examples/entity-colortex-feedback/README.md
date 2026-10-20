# Entity colortex feedback

This original diagnostic pack gives `colortex4` a red clear value, then makes the entity pass sample and write that same buffer. Magenta cow pixels mean the Vulkan snapshot was available to the shader while the source image remained an output attachment; green pixels indicate a failed read.

The graphics smoke job compares active and disabled shader frames. Veyra captures the snapshot before the first draw that uses the pipeline; this fixture validates that explicit geometry feedback behavior and does not claim parity with Iris' racy same-image sampling behavior.
