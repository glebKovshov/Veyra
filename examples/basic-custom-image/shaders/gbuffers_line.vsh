#version 120

uniform usampler3D voxel_sampler;
flat varying uint voxelVertexValue;
varying vec2 lmCoord;

void main() {
    gl_Position = ftransform();
    lmCoord = gl_MultiTexCoord1.st;
    voxelVertexValue = texelFetch(voxel_sampler, ivec3(0), 0).r;
}
