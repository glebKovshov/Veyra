#version 120
varying vec3 LightC;

void main() {
    gl_Position = ftransform();
    LightC = vec3(1.0, 0.0, 1.0);
}
