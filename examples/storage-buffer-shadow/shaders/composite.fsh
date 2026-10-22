#version 430
layout(std430, binding = 0) readonly buffer VeyraSmokeStorageBuffer {
    uint vertexCount;
} veyraSmokeBuffer;

void main() {
    gl_FragColor = veyraSmokeBuffer.vertexCount > 0u
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(1.0, 0.0, 0.0, 1.0);
}
