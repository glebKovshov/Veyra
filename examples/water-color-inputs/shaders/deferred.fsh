#version 120
/* RENDERTARGETS:5 */
/*
const int colortex5Format = RGB8;
*/
void main() {
    float stripe = mod(floor(gl_FragCoord.x), 2.0);
    gl_FragColor = vec4(stripe, 0.0, 1.0 - stripe, 0.0);
}
