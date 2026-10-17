#version 120

uniform sampler2D texture;
uniform float alphaTestRef;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    color.rgb = mix(color.rgb, vec3(1.0, 0.12, 0.08), 0.85);
    if (color.a < alphaTestRef) {
        discard;
    }
    gl_FragColor = color;
}
