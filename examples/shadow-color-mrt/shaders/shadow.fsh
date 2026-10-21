#version 120
const int shadowMapResolution=256;
const float shadowDistance=32.0;
/* RENDERTARGETS:0,1 */
varying vec4 color;
void main() {
    gl_FragData[0] = vec4(0.2, 0.0, 0.0, 1.0);
    gl_FragData[1] = color;
}
