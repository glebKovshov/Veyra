#version 430
layout(local_size_x=8, local_size_y=8, local_size_z=4) in;
uniform usampler3D voxel_sampler;
writeonly uniform uimage3D floodfill_img;
writeonly uniform uimage2D floodfill2d_img;
void main() {
    ivec3 position=ivec3(gl_GlobalInvocationID);
    if (any(greaterThanEqual(position,imageSize(floodfill_img)))) return;
    uint shadowValue=texelFetch(voxel_sampler,ivec3(0),0).r;
    imageStore(floodfill_img,position,uvec4(shadowValue+37u));
    if (all(equal(position,ivec3(0)))) {
        imageStore(floodfill2d_img,ivec2(0),uvec4(shadowValue+37u));
    }
}
