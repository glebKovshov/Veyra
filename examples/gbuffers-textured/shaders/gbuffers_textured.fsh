#version 150

uniform sampler2D texture;
in vec2 texcoord;
out vec4 fragColor;

void main() {
    vec4 sampled = texture2D(texture, texcoord);
    fragColor = vec4(sampled.rgb * vec3(0.88, 0.96, 1.0), sampled.a);
}
