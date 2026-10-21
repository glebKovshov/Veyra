#version 120

varying float veyraDefaultAttributes;

void main() {
    if (veyraDefaultAttributes < 0.5) discard;
    gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
}
