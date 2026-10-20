#version 130

varying vec2 texcoord;
flat varying vec4 color;
uniform sampler2D texture;

void main() {
    vec4 glintMask = texture2D(texture, texcoord);
    gl_FragColor = vec4(0.0, color.g, 0.0, glintMask.a);
}
