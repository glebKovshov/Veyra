#version 120

uniform float frameTimeCounter;

varying vec4 color;

void main() {
    float pulse = 0.9 + 0.1 * (0.5 + 0.5 * sin(frameTimeCounter * 0.5));
    gl_FragColor = vec4(color.rgb * pulse, color.a);
}
