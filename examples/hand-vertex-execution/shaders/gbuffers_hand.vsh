#version 120
attribute vec4 mc_midTexCoord;
varying vec3 LightC;

void main() {
    gl_Position = ftransform();
    vec2 midpointDelta = abs(mc_midTexCoord.st - gl_MultiTexCoord0.st);
    float midpointValid = step(0.001, mc_midTexCoord.s) * step(mc_midTexCoord.s, 0.999)
            * step(0.001, mc_midTexCoord.t) * step(mc_midTexCoord.t, 0.999)
            * step(0.003, midpointDelta.s) * step(0.003, midpointDelta.t)
            * (1.0 - step(0.5001, midpointDelta.s)) * (1.0 - step(0.5001, midpointDelta.t));
    LightC = vec3(1.0, 0.0, 1.0) * midpointValid;
}
