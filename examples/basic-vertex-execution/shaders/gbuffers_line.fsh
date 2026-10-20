#version 120
/* DRAWBUFFERS:06 */

uniform sampler2D noisetex;
uniform sampler2D specular;
uniform sampler2D colortex6;
varying vec3 normal;

void main() {
    vec2 sampleUv = normal.xy * 0.5 + 0.5;
    float inputSignal = dot(texture2D(noisetex, sampleUv), vec4(0.25))
            + dot(texture2D(specular, sampleUv), vec4(0.25))
            + dot(texture2D(colortex6, sampleUv), vec4(0.25));
    gl_FragData[0] = vec4(inputSignal * 0.01, 1.0, inputSignal * 0.01, 1.0);
    gl_FragData[1] = vec4(1.0, inputSignal * 0.02, 1.0, 1.0);
}
