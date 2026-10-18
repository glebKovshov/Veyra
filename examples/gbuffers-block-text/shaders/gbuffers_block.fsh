#version 150

uniform sampler2D texture;
uniform float alphaTestRef;
in vec2 texcoord;
in vec4 glcolor;
out vec4 fragColor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    if (color.a < alphaTestRef) {
        discard;
    }
    fragColor = color;
}
