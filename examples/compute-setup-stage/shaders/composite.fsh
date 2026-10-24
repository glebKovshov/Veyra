#version 430
uniform float viewWidth;
uniform float viewHeight;
layout(std430, binding=0) readonly buffer VeyraState {
    uint runs;
    uint firstWidth;
    uint width;
    uint height;
    uint overflow;
} state;
void main() {
    uint expectedRuns=uint(viewWidth) == state.firstWidth ? 1u : 2u;
    bool valid=state.runs == expectedRuns && state.overflow == 0u
            && state.width == uint(viewWidth) && state.height == uint(viewHeight);
    gl_FragColor=valid
            ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
