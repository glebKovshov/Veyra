#version 120
uniform sampler2D marker;

void main() {
    gl_FragColor = texture2D(marker, vec2(0.5));
}
