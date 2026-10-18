#version 120

varying vec2 texcoord;

void main() {
    gl_TexCoord[0].xy = gl_MultiTexCoord0.xy * 0.92 + 0.04;
    gl_Position = ftransform();
}
