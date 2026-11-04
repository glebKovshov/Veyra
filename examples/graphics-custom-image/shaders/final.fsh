#version 430
uniform sampler2D colortex1;
uniform usampler3D voxel_sampler;
uniform usampler2D marker_sampler;
varying vec2 texcoord;
void main() {
    bool ready=texelFetch(voxel_sampler,ivec3(0),0).r == 5u && texelFetch(marker_sampler,ivec2(0),0).r == 7u;
    gl_FragColor=ready ? texture2D(colortex1,texcoord) : vec4(1.0,0.0,0.0,1.0);
}
