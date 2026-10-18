#version 120

uniform sampler2D texture;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform bool is_sneaking;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    float pulse = 0.85 + 0.15 * (0.5 + 0.5 * sin(frameTimeCounter * 2.0));
    color.rgb *= pulse;
    if (is_sneaking) {
        color.rgb = color.bgr;
    }
    color.rgb = mix(color.rgb, vec3(0.25, 0.55, 0.9), rainStrength * 0.25);
    gl_FragColor = color;
}
