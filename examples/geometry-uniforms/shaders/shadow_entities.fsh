#version 120

uniform sampler2D texture;
uniform float frameTimeCounter;
uniform float rainStrength;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    float alphaCutoff = alphaTestRef + 0.05 * (0.5 + 0.5 * sin(frameTimeCounter * 2.0));
    alphaCutoff += rainStrength * 0.02;
    if (albedo.a < alphaCutoff) {
        discard;
    }
    gl_FragColor = albedo;
}
