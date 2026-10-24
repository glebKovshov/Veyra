#version 430
layout(std430, binding=0) readonly buffer VeyraPreShadowState {
    uint ready;
    uint value;
    uint wrongOrder;
    uint wrongResolution;
} state;
void main() {
    bool valid=state.ready == 1u && state.value == 0u
            && state.wrongOrder == 0u && state.wrongResolution == 0u;
    gl_FragColor=valid ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
