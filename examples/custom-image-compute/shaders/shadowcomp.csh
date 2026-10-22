#version 430
layout(local_size_x=8, local_size_y=8, local_size_z=1) in;
const vec2 workGroupsRender=vec2(0.01f,0.01f);
uniform float framemod2;
uniform usampler3D voxel_sampler;
writeonly uniform uimage3D floodfill_img;
layout(std430, binding=0) buffer DispatchArgs { uvec3 groups; } dispatchArgs;
void main() {
    ivec3 position=ivec3(gl_GlobalInvocationID);
    if (all(equal(gl_GlobalInvocationID, uvec3(0)))) dispatchArgs.groups=uvec3(1u,1u,1u);
    if (any(greaterThanEqual(position,imageSize(floodfill_img)))) return;
    uint shadowValue=texelFetch(voxel_sampler,ivec3(0),0).r;
    imageStore(floodfill_img,position,uvec4(shadowValue+36u+uint(framemod2)*0u));
}
