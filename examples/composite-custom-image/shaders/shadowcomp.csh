#version 430
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const ivec3 workGroups=ivec3(4,4,4);
uniform float framemod2;
uniform usampler3D voxel_sampler;
writeonly uniform uimage3D floodfill_img;
void main() {
    uint shadowValue=texelFetch(voxel_sampler,ivec3(0),0).r;
    imageStore(floodfill_img,ivec3(gl_GlobalInvocationID),uvec4(shadowValue+37u+uint(framemod2)*0u));
}
