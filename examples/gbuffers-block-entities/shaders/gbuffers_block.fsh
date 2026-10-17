#version 150

uniform sampler2D texture;
in vec2 texcoord;
in vec4 glcolor;
out vec4 fragColor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    fragColor = vec4(color.rgb * vec3(0.2, 1.0, 0.2), color.a);
}
