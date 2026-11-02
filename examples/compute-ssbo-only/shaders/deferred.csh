#version 430
#if !defined(IRIS_FEATURE_COMPUTE_SHADERS) || !defined(IRIS_FEATURE_SSBO)
#error Optional compute and SSBO features were not negotiated
#endif
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const ivec3 workGroups=ivec3(1,1,1);
layout(std430, binding=0) buffer VeyraState { uint value; } state;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
void main() {
    vec4 probe = vec4(0.25, 0.5, 0.75, 1.0);
    state.value=distance(gbufferProjection * gbufferProjectionInverse * probe, probe) < 0.001 ? 42u : 0u;
}
