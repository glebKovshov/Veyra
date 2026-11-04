#version 430
layout(local_size_x=1) in;
const ivec3 workGroups=ivec3(1,1,1);
layout(rgba16f) uniform image2D colorimg4;
void main(){
    float previous=imageLoad(colorimg4,ivec2(1,0)).r;
    imageStore(colorimg4,ivec2(2,0),vec4(previous+0.25,0.0,0.0,1.0));
}
