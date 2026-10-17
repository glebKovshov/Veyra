#version 150

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    color.rgb = mix(color.rgb, vec3(0.12, 0.58, 0.96), 0.45);
    gl_FragColor = color;
}
