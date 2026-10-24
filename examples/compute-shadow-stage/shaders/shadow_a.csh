#version 430
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const ivec3 workGroups=ivec3(1,1,1);
layout(std430, binding=0) buffer VeyraPreShadowState {
    uint ready;
    uint value;
    uint wrongOrder;
    uint wrongResolution;
} state;
void main() {
    if (state.ready != 2u || state.value != 10u) atomicOr(state.wrongOrder,2u);
    state.ready=3u;
    state.value=42u;
}
