#version 120
/* RENDERTARGETS:5 */
/*
const int colortex5Format = RGB8;
*/
uniform sampler2D colortex0;
void main() {
    float stripe = mod(floor(gl_FragCoord.x), 2.0);
    float missingAlpha = 1.0 - texture2D(colortex0, vec2(0.5)).a;
    gl_FragColor = vec4(stripe, missingAlpha, 1.0 - stripe, 0.0);
}
