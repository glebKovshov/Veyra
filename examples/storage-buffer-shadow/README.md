# Shadow-to-post storage buffer

This fixture allocates one four-byte `bufferObject.0`, clears it before the shadow pass,
increments it from the terrain shadow vertex shader, and reads it in `composite.fsh`.
The frame is magenta only when the Vulkan storage-buffer write and post-pass read both work.
