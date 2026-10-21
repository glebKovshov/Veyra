#version 120

varying vec4 damageSignal;

void main() {
    gl_Position = ftransform();
    damageSignal = vec4(1.0, 0.05, 0.8, 1.0);
}
