#version 120

flat in int mat;
varying float recolor;

void main() {
    gl_FragColor = mat == 15119 && recolor > 0.5
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 0.0, 0.0, 1.0);
}
