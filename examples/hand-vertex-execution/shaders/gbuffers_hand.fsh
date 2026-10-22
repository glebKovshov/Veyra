#version 120
varying vec3 LightC;

void main() {
    gl_FragData[0] = vec4(LightC, 1.0);
}
