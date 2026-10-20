#version 120

void main() {
    // Move all terrain outside clip space. Lavapipe smoke verifies this entrypoint executes.
    gl_Position = vec4(4.0, 4.0, 4.0, 1.0);
}
