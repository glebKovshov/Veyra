#version 120

uniform sampler2DShadow shadowtex0;
varying vec2 texcoord;

float sampleShadow(sampler2DShadow depthSampler, vec3 coordinate) {
    return shadow2D(depthSampler, coordinate).x;
}

void main() {
    float visibility = sampleShadow(shadowtex0, vec3(texcoord, 0.5));
    gl_FragColor = vec4(vec3(visibility), 1.0);
}
