#version 450
/* RENDERTARGETS:0,1 */
uniform sampler2D normals;
uniform sampler2D specular;
varying vec2 texcoord;
void main() {
    vec4 normalData = textureLod(normals, texcoord, 2.0);
    vec4 specularData = textureLod(specular, texcoord, 2.0);
    bool normalFrame = textureQueryLevels(normals) >= 3 && normalData.r > 0.95
            && normalData.g > 0.45 && normalData.g < 0.55
            && normalData.b > 0.45 && normalData.b < 0.55;
    bool specularFrame = textureQueryLevels(specular) >= 3 && specularData.r > 0.8
            && specularData.g < 0.1 && specularData.b > 0.7 && specularData.a > 0.9;
    vec4 signal = normalFrame
            ? (specularFrame ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.95, 0.02, 0.02, 1.0))
            : (specularFrame ? vec4(0.02, 0.02, 0.95, 1.0) : vec4(0.02, 0.8, 0.05, 1.0));
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
