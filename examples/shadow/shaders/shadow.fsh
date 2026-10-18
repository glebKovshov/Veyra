#version 120

const int shadowMapResolution = 2048;
const float shadowDistance = 96.0;
const float shadowDistanceRenderMul = 1.0;
const float shadowIntervalSize = 2.0;
const float sunPathRotation = 0.0;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < alphaTestRef) {
        discard;
    }
    gl_FragColor = vec4(1.0);
}
