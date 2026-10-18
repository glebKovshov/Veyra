#version 330

varying vec3 normal;

void main() {
    gl_FragColor = vec4(normalize(normal) * 0.5 + 0.5, 1.0);
}
