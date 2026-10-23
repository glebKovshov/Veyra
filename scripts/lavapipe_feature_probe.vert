#version 450

layout(set = 0, binding = 0, std430) buffer FeatureProbeBuffer {
    uint value;
} probeBuffer;

void main() {
    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
    probeBuffer.value = 1u;
}
