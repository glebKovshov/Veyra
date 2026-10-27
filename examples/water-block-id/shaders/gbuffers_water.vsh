#version 120

varying vec3 waterNormal;
flat out int mat;
attribute vec4 mc_Entity;

void main() {
    waterNormal = gl_Normal;
    mat = int(mc_Entity.x);
    gl_Position = ftransform();
}
