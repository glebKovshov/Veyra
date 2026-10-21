#version 120

varying vec4 damageSignal;

void main() {
    gl_FragColor = damageSignal;
}
