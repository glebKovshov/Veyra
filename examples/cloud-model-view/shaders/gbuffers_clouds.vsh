#version 120

varying float dist;

void main() {
    gl_Position = ftransform();
    dist = length(gl_ModelViewMatrix * gl_Vertex);
}
