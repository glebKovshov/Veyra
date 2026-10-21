#version 130

flat varying float particleVertexSignal;

void main() {
    if (particleVertexSignal < 0.5) discard;
    gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
}
