#version 120
/* RENDERTARGETS:0,4 */
uniform sampler2D colortex4;
const vec4 colortex4ClearColor = vec4(1.0, 0.0, 0.0, 1.0);
void main() {
    vec4 previous = texture2D(colortex4, vec2(0.5));
    bool correct = previous.r > 0.8 && previous.g < 0.1 && previous.b < 0.1;
    vec4 signal = correct ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = previous;
}
