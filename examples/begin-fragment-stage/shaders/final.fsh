#version 120
uniform sampler2D colortex1;
varying vec2 texcoord;

void main() {
    vec4 beginSignal = texture2D(colortex1, texcoord);
    bool ranBeforeShadows = beginSignal.r > 0.9 && beginSignal.g < 0.1 && beginSignal.b > 0.9;
    gl_FragColor = ranBeforeShadows ? vec4(1.0, 0.0, 1.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
}
