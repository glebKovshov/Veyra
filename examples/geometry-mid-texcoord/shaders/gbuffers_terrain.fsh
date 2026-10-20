#version 120

varying vec2 signMidCoordPos;

void main() {
    gl_FragColor = signMidCoordPos.x > 0.0 && signMidCoordPos.y > 0.0
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 1.0, 0.0, 1.0);
}
