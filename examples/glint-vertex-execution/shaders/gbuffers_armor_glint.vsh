#version 130

varying vec2 texcoord;
flat varying vec4 color;

void main() {
    gl_Position = ftransform();
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    color = gl_Color * 0.0 + vec4(0.0, 1.0, 0.0, 1.0);
}
