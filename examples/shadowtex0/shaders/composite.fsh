#version 120

uniform sampler2DShadow shadowtex0;
varying vec2 texcoord;

void main() {
    float visibility = shadow2D(shadowtex0, vec3(texcoord, 0.5)).r;
    gl_FragColor = vec4(vec3(visibility), 1.0);
}
