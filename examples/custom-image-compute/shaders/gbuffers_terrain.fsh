#version 430
uniform usampler3D floodfill_sampler;
uniform usampler2D floodfill2d_sampler;
void main() {
    uint computed=texelFetch(floodfill_sampler,ivec3(0),0).r;
    uint computed2d=texelFetch(floodfill2d_sampler,ivec2(0),0).r;
    gl_FragColor=computed==42u && computed2d==42u
            ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
