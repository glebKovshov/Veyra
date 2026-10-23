#version 120
uniform sampler2D colortex1;
varying vec2 texcoord;
void main() { gl_FragColor = texture2D(colortex1, texcoord); }
