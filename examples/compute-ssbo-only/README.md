# SSBO-only compute diagnostic

`deferred.csh` writes 42 to the declared `bufferObject.0`. `composite.fsh` reads that value and draws magenta only when the compute dispatch and storage-buffer descriptor are active. This pack requires `COMPUTE_SHADERS` but declares no custom images and does not require `CUSTOM_IMAGES`. Disabling the pack removes the magenta signal.
