#version 120
uniform sampler2D colortex0;
uniform sampler2D depthtex0;
varying vec2 texcoord;

void main() {
    gl_FragColor = texture2D(depthtex0, texcoord).r < 1.0
            ? texture2D(colortex0, texcoord) : vec4(0.0, 0.0, 0.0, 1.0);
}
