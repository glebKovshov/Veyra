#version 120

varying vec2 signMidCoordPos;
varying vec2 absMidCoordPos;

void main() {
    gl_FragColor = signMidCoordPos.x > 0.0 && signMidCoordPos.y > 0.0
            && absMidCoordPos.x > 0.0 && absMidCoordPos.y > 0.0
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 1.0, 0.0, 1.0);
}
