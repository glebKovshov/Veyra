#version 120

uniform sampler2D texture;
uniform ivec2 eyeBrightness;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    vec2 brightness = clamp(vec2(eyeBrightness) / 240.0, 0.0, 1.0);
    gl_FragColor = vec4(brightness.x, brightness.y, 0.0, albedo.a);
}
