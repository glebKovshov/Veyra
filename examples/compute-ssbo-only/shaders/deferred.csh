#version 430
#if !defined(IRIS_FEATURE_COMPUTE_SHADERS) || !defined(IRIS_FEATURE_SSBO)
#error Optional compute and SSBO features were not negotiated
#endif
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const ivec3 workGroups=ivec3(1,1,1);
layout(std430, binding=0) buffer VeyraState { uint value; } state;
void main() {
    state.value=42u;
}
