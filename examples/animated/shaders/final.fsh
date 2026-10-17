#version 330 compatibility

uniform sampler2D colortex0;
uniform float frameTimeCounter;
in vec2 texcoord;

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    float pulse = 0.92 + 0.08 * sin(frameTimeCounter * 2.0);
    gl_FragColor = vec4(color.rgb * pulse, color.a);
}
