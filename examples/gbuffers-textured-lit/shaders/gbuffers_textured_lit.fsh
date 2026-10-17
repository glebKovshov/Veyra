#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    gl_FragColor = texture2D(texture, texcoord) * glcolor;
}
