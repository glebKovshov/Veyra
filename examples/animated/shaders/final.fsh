#version 330 compatibility

uniform sampler2D colortex0;
uniform float frameTimeCounter;
in vec2 texcoord;

#include "/lib/pulse.glsl"

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    float pulse = veyraPulse(frameTimeCounter);
    gl_FragColor = vec4(color.rgb * pulse, color.a);
}
