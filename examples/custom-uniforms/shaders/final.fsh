#version 120
uniform sampler2D colortex1;
uniform int veyraDayTick;
uniform float veyraScaled;
varying vec2 texcoord;
void main() {
    // The post pass must receive the same per-frame values as terrain.
    bool custom = veyraDayTick == 7 && abs(veyraScaled - 2.0) < 0.001;
    gl_FragColor = custom ? texture2D(colortex1, texcoord) : vec4(0.02, 0.8, 0.05, 1.0);
}
