#version 120

flat in int mat;
flat in vec2 mc_Entity;

void main() {
    gl_FragColor = mat == 27102 && mc_Entity.y > 0.5
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 0.0, 0.0, 1.0);
}
