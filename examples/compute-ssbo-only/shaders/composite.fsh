#version 430
#if !defined(IRIS_FEATURE_COMPUTE_SHADERS) || !defined(IRIS_FEATURE_SSBO)
#error Optional compute and SSBO features were not negotiated
#endif
layout(std430, binding=0) readonly buffer VeyraState { uint value; } state;
void main() {
    gl_FragColor=state.value==42u
            ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
