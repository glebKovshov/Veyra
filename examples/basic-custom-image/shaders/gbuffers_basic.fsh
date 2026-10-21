#version 120
/* DRAWBUFFERS:06 */
uniform usampler3D voxel_sampler;
void main() {
    uint value = texelFetch(voxel_sampler, ivec3(0), 0).r;
    vec4 signal = value == 5u ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
