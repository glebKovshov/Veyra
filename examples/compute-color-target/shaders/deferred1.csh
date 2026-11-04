#version 430
layout(local_size_x=1) in;
const ivec3 workGroups=ivec3(1,1,1);
layout(rgba16f) writeonly uniform image2D colorimg4;
uniform sampler2D colortex4;
void main(){
    float previous=texelFetch(colortex4,ivec2(0),0).r;
    imageStore(colorimg4,ivec2(1,0),vec4(previous+0.25,0.0,0.0,1.0));
}
