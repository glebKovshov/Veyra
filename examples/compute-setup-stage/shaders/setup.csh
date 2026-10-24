#version 430
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const vec2 workGroupsRender=vec2(1.0);
uniform float viewWidth;
uniform float viewHeight;
layout(std430, binding=0) buffer VeyraState {
    uint runs;
    uint firstWidth;
    uint width;
    uint height;
    uint overflow;
} state;
void main() {
    if (all(equal(gl_GlobalInvocationID,uvec3(0)))) {
        state.runs++;
        if (state.firstWidth == 0u) state.firstWidth=uint(viewWidth);
        state.width=uint(viewWidth);
        state.height=uint(viewHeight);
    } else atomicOr(state.overflow,1u);
}
