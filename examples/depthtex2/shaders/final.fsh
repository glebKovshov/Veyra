#version 120

uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform sampler2D depthtex2;
varying vec2 texcoord;

void main() {
    float depth;
    if (texcoord.x < 0.333333) {
        depth = texture2D(depthtex0, texcoord).r;
    } else if (texcoord.x < 0.666667) {
        depth = texture2D(depthtex1, texcoord).r;
    } else {
        depth = texture2D(depthtex2, texcoord).r;
    }
    gl_FragColor = vec4(vec3(depth), 1.0);
}
