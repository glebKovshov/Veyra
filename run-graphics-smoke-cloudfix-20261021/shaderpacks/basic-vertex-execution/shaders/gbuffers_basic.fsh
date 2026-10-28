#version 120
/* DRAWBUFFERS:06 */

uniform sampler2D lightmap;
uniform sampler2D noisetex;
uniform sampler2D specular;
uniform sampler2D colortex6;
varying vec2 lmCoord;

void main() {
    float inputSignal = dot(texture2D(lightmap, lmCoord), vec4(0.25))
            + dot(texture2D(noisetex, lmCoord), vec4(0.25))
            + dot(texture2D(specular, lmCoord), vec4(0.25))
            + dot(texture2D(colortex6, lmCoord), vec4(0.25));
    gl_FragData[0] = vec4(inputSignal * 0.01, 1.0, inputSignal * 0.01, 1.0);
    gl_FragData[1] = vec4(1.0, inputSignal * 0.02, 1.0, 1.0);
}
