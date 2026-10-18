#version 120

uniform sampler2D texture;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform mat4 shadowModelViewInverse;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    float alphaCutoff = alphaTestRef + 0.05 * (0.5 + 0.5 * sin(frameTimeCounter * 2.0));
    alphaCutoff += rainStrength * 0.02;
    vec3 shadowUp = normalize(mat3(shadowModelViewInverse) * vec3(0.0, 1.0, 0.0));
    alphaCutoff += abs(shadowUp.y) * 0.005;
    if (albedo.a < alphaCutoff) {
        discard;
    }
    gl_FragColor = albedo;
}
