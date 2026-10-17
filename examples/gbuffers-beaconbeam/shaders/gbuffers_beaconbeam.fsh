#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 color;

void main() {
    vec4 beam = texture2D(texture, texcoord) * color;
    gl_FragColor = vec4(beam.b, beam.r, beam.g, beam.a);
}
