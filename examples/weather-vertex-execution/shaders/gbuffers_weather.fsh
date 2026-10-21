#version 130

flat varying float weatherVertexSignal;

void main() {
    if (weatherVertexSignal < 0.5) discard;
    gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
}
