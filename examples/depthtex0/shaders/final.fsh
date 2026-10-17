#version 120

uniform sampler2D depthtex0;
varying vec2 texcoord;

void main() {
    float depth = texture2D(depthtex0, texcoord).r;
    gl_FragColor = vec4(vec3(depth), 1.0);
}
