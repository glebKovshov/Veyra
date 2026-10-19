#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    gl_FragColor = vec4(fract(abs(viewPos) * 0.125), albedo.a);
}
