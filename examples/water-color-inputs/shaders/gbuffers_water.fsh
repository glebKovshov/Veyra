#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D gaux1;
uniform sampler2D gaux2;
uniform sampler2D colortex5;
uniform sampler2D colortex7;
// Common pack headers declare noise even when the selected profile never reads it.
uniform sampler2D noisetex;
uniform float viewWidth;
uniform float viewHeight;
const vec4 colortex7ClearColor = vec4(0.25, 0.5, 0.75, 1.0);
void main() {
    vec2 uv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
    vec4 opaque = texture2D(gaux1, uv);
    vec4 deferred = texture2D(gaux2, uv);
    vec4 alias = texture2D(colortex5, uv);
    vec4 initialized = texture2D(colortex7, uv);
    bool correct = opaque.r > 0.8 && opaque.b < 0.1
        && deferred.b > 0.8 && deferred.r < 0.1 && abs(alias.b - deferred.b) < 0.01
        && abs(initialized.g - 0.5) < 0.02;
    vec4 signal = correct ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
