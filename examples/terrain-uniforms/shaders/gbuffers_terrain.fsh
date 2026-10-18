#version 120

uniform sampler2D texture;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform int isEyeInWater;
uniform bool is_sneaking;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < alphaTestRef) {
        discard;
    }

    vec3 stateColor = vec3(fract(frameTimeCounter * 0.25), rainStrength,
            isEyeInWater > 0 ? 1.0 : 0.0);
    if (is_sneaking) {
        stateColor = stateColor.bgr;
    }
    gl_FragColor = vec4(mix(albedo.rgb, stateColor, 0.35), albedo.a);
}
