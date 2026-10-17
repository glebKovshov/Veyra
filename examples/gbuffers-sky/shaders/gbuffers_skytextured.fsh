#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;

void main() {
    vec4 skyTexture = texture2D(gtexture, texcoord);
    gl_FragColor = skyTexture * vec4(0.78, 0.86, 1.0, 1.0);
}
