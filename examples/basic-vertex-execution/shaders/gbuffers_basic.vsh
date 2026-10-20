#version 120

varying vec2 lmCoord;

void main() {
    gl_Position = ftransform();
    lmCoord = gl_MultiTexCoord1.st;
}
