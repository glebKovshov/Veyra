# Begin-stage compute diagnostic

`begin.csh` runs at the head of the level frame, before the shadow map, and writes 42 to `bufferObject.0`. `composite.fsh` reads that value and draws magenta only when the pre-shadow compute dispatch and storage-buffer descriptor are active. This pack requires `COMPUTE_SHADERS` and declares no custom images. Disabling the pack removes the magenta signal.
