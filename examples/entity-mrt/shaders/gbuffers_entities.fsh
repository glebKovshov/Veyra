#version 120
/* RENDERTARGETS:0,1 */
const vec4 colortex1ClearColor = vec4(0.0);
void main() {
    gl_FragData[0] = vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
}
