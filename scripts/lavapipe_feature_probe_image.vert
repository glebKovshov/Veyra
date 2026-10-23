#version 450

layout(set = 0, binding = 0, r32ui) uniform uimage3D probeImage;

void main() {
    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
    imageAtomicMax(probeImage, ivec3(0), 1u);
}
