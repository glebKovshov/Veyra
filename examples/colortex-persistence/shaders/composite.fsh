#version 120

uniform sampler2D colortex2;
varying vec2 texcoord;

const bool colortex2Clear = false;
const vec4 colortex2ClearColor = vec4(0.0);

/* RENDERTARGETS: 2 */
void main() {
    vec4 accumulated = texture2D(colortex2, texcoord);
    gl_FragColor = min(accumulated + vec4(0.008, 0.008, 0.008, 0.0), vec4(1.0));
}
