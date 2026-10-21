#version 130

flat varying float weatherVertexSignal;

void main() {
    gl_Position = ftransform();
    weatherVertexSignal = 1.0;
}
