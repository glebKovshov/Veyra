#version 430
layout(local_size_x=8, local_size_y=8, local_size_z=1) in;
const vec2 workGroupsRender=vec2(0.01f,0.01f);
uniform float framemod2;
uniform usampler3D voxel_sampler;
writeonly uniform uimage3D floodfill_img;
void main() {
    ivec3 position=ivec3(gl_GlobalInvocationID);
    if (any(greaterThanEqual(position,imageSize(floodfill_img)))) return;
    uint shadowValue=texelFetch(voxel_sampler,ivec3(0),0).r;
    imageStore(floodfill_img,position,uvec4(shadowValue+37u+uint(framemod2)*0u));
}
