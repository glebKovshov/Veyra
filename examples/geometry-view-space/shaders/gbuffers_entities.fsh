#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 viewNormal;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    vec3 positionColor = fract(abs(viewPos) * 0.125);
    vec3 normalColor = normalize(viewNormal) * 0.5 + 0.5;
    gl_FragColor = vec4(mix(positionColor, normalColor, 0.35), albedo.a);
}
