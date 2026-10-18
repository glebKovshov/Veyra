#version 120

varying vec2 texcoord;
uniform sampler2D colortex0;

void main() {
    gl_FragColor = vec4(texture2D(colortex0, texcoord).rgb, 1.0);
}
