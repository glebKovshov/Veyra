#version 120

uniform float frameTimeCounter;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
varying vec2 texcoord;

void main() {
    float inset = 0.04 + 0.005 * sin(frameTimeCounter);
    gl_TexCoord[0].xy = gl_MultiTexCoord0.xy * (1.0 - 2.0 * inset) + inset;
    gl_Position = gbufferProjectionInverse * gbufferProjection * ftransform();
}
