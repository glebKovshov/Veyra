#version 450
/* RENDERTARGETS:0,1 */
uniform sampler2D normals;
varying vec2 texcoord;
void main() {
    vec4 data = textureLod(normals, texcoord, 2.0);
    bool valid = textureQueryLevels(normals) >= 3 && data.r > 0.95
            && data.g > 0.45 && data.g < 0.55 && data.b > 0.45 && data.b < 0.55;
    vec4 signal = valid ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
