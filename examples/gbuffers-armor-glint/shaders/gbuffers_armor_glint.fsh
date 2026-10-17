#version 120

uniform sampler2D gtexture;
varying vec2 texcoord;

void main() {
    vec4 glint = texture2D(gtexture, texcoord);
    gl_FragColor = vec4(1.0, 0.0, 0.8, glint.a);
}
