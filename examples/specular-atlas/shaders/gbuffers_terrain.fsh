#version 450
/* RENDERTARGETS:0,1 */
uniform sampler2D specular;
varying vec2 texcoord;
void main() {
    vec4 data = textureLod(specular, texcoord, 2.0);
    bool valid = textureQueryLevels(specular) >= 3 && data.r > 0.8 && data.g < 0.1 && data.b > 0.7 && data.a > 0.9;
    vec4 signal = valid ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
