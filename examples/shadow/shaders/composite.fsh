#version 330 compatibility

const float entityShadowDistanceMul = 0.125;

uniform sampler2D colortex0;
in vec2 texcoord;

void main() {
    gl_FragColor = texture2D(colortex0, texcoord);
}
