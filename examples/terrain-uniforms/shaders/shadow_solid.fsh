#version 120

uniform sampler2D texture;
uniform float frameTimeCounter;
uniform float rainStrength;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < alphaTestRef) {
        discard;
    }
    gl_FragColor = vec4(rainStrength, fract(frameTimeCounter), 0.0, albedo.a);
}
