#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    color.rgb = mix(color.rgb, vec3(0.1, 0.75, 0.95), 0.35 + MC_HAND_DEPTH);
    gl_FragColor = color;
}
