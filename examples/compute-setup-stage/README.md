# Setup-stage compute diagnostic

`setup.csh` runs when the shader pipeline is installed and writes 42 to `bufferObject.0`. The ordinary composite fragment reads the same SSBO on later frames and draws magenta only while its contents persist. This pack requires `COMPUTE_SHADERS` and declares no custom images.
