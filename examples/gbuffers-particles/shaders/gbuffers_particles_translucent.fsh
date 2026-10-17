#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 particle = texture2D(texture, texcoord) * glcolor;
    gl_FragColor = vec4(particle.r, particle.b, particle.g, particle.a);
}
