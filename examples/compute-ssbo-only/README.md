# SSBO-only compute diagnostic

`deferred.csh` writes 42 to the declared `bufferObject.0`. `composite.fsh` reads that value and draws magenta only when the compute dispatch and storage-buffer descriptor are active. This pack requires `COMPUTE_SHADERS` and requests optional `COMPUTE_SHADERS SSBO` macros; both stages fail preprocessing if negotiation is broken. It declares no custom images and does not require `CUSTOM_IMAGES`. Disabling the pack removes the magenta signal.

The compute write also requires `gbufferProjection * gbufferProjectionInverse` to preserve a probe vector, checking the screen-space matrix suppliers on the GPU.
