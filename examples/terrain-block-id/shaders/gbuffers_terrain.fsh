#version 120

flat in int mat;

void main() {
    gl_FragColor = mat == 27101
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 0.0, 0.0, 1.0);
}
