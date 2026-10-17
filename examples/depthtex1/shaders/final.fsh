#version 120

uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
varying vec2 texcoord;

void main() {
    float mainDepth = texture2D(depthtex0, texcoord).r;
    float solidDepth = texture2D(depthtex1, texcoord).r;
    float difference = clamp(abs(mainDepth - solidDepth) * 512.0, 0.0, 1.0);
    gl_FragColor = vec4(solidDepth, mainDepth, difference, 1.0);
}
