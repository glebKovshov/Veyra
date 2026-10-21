#version 430
uniform usampler3D floodfill_sampler;
void main() {
    uint computed=texelFetch(floodfill_sampler,ivec3(0),0).r;
    gl_FragColor=computed==42u ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
