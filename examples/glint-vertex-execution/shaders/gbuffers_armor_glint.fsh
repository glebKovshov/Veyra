#version 130

varying vec2 texcoord;
flat varying vec4 color;
uniform sampler2D texture;

void main() {
    gl_FragColor = texture2D(texture, texcoord) * color;
}
