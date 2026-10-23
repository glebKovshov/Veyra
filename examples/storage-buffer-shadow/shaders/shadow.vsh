#version 430
layout(std430, binding = 0) buffer VeyraSmokeStorageBuffer {
    uint vertexCount;
} veyraSmokeBuffer;

void main() {
    gl_Position = ftransform();
    atomicAdd(veyraSmokeBuffer.vertexCount, 1u);
}
