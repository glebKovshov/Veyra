#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 eyes = texture2D(texture, texcoord) * glcolor;
    gl_FragColor = vec4(eyes.g, eyes.r, eyes.b, eyes.a);
}
