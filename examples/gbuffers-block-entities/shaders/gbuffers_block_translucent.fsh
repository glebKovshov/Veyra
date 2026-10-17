#version 150

uniform sampler2D texture;
in vec2 texcoord;
in vec4 glcolor;
out vec4 fragColor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    fragColor = vec4(mix(color.rgb, vec3(0.2, 0.58, 1.0), 0.45), color.a * 0.7);
}
