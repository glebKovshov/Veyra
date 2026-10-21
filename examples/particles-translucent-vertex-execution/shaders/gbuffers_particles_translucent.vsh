#version 130

flat varying float particleVertexSignal;

void main() {
    gl_Position = ftransform();
    particleVertexSignal = 1.0;
}
