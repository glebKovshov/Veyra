#version 430
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const vec2 workGroupsRender=vec2(1.0);
layout(std430, binding=0) buffer VeyraPreShadowState {
    uint ready;
    uint value;
    uint wrongOrder;
    uint wrongResolution;
} state;
void main() {
    uvec3 invocation=gl_GlobalInvocationID;
    if (all(equal(invocation,uvec3(0)))) {
        if (state.ready != 1u) atomicOr(state.wrongOrder,1u);
        state.ready=2u;
    }
    if (all(equal(invocation,uvec3(31,31,0)))) state.value=10u;
    // Both the far corner and overflow sentinel distinguish shadow sizing from viewport sizing.
    if (invocation.x >= 32u || invocation.y >= 32u) atomicOr(state.wrongResolution,1u);
}
