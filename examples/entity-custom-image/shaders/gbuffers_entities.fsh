#version 120
uniform usampler3D voxel_sampler;

void main() {
    uint value = texelFetch(voxel_sampler, ivec3(0), 0).r;
    gl_FragColor = value == 5u ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
}
