#version 430
/* RENDERTARGETS:0,1 */
const vec4 colortex1ClearColor=vec4(0.0);
#ifndef IRIS_FEATURE_CUSTOM_IMAGES
#error graphics-only pack lost its optional feature
#endif
uniform usampler3D voxel_sampler;
uniform usampler2D marker_sampler;
void main() {
    bool ready=texelFetch(voxel_sampler,ivec3(0),0).r == 5u && texelFetch(marker_sampler,ivec2(0),0).r == 7u;
    vec4 signal=ready ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
    gl_FragData[0]=signal;
    gl_FragData[1]=signal;
}
