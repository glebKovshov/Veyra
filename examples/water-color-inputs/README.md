# Water color inputs

An original diagnostic pack for water above an opaque floor. Terrain writes red to colortex4 and green to colortex5; deferred writes blue to colortex5's next ping-pong side. Water samples gaux1, gaux2 and the modern colortex5 alias, plus a half-resolution read-only colortex7 initialized to its declared clear color. It also samples colortex4 while writing back to colortex4; Veyra captures a deterministic snapshot when the water pipeline activates.

Magenta means all four reads matched their expected contents. Green indicates a failed read or stale buffer side. The final pass shows the water signal through colortex1. The software Vulkan integration matrix compares it against shaders disabled.

Water and entity-family geometry can read auxiliary colortex1..15 buffers. When a sampled buffer is also an output, Veyra copies its contents before the first draw using that pipeline and samples the separate image, avoiding Vulkan feedback while keeping the source image available as an output. This defines Veyra's behavior and does not claim to reproduce Iris' documented same-image race behavior.
