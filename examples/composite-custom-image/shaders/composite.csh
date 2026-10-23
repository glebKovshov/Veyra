#version 430
layout(local_size_x=1, local_size_y=1, local_size_z=1) in;
const ivec3 workGroups=ivec3(1,1,1);
uniform uimage2D post_img;
void main() {
    uint previous=imageLoad(post_img,ivec2(0)).r;
    imageStore(post_img,ivec2(0),uvec4(previous+11u));
}
