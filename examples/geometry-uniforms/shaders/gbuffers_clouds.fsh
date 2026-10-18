#version 120

uniform float frameTimeCounter;
uniform mat4 gbufferProjectionInverse;
uniform int moonPhase;

varying vec4 color;

void main() {
    float pulse = 0.9 + 0.1 * (0.5 + 0.5 * sin(frameTimeCounter * 0.5));
    float projectionScale = clamp(abs(gbufferProjectionInverse[2][2]), 0.0, 1.0);
    float lunarTint = 0.96 + 0.04 * float(moonPhase) / 7.0;
    gl_FragColor = vec4(color.rgb * pulse * (0.98 + 0.02 * projectionScale) * lunarTint, color.a);
}
