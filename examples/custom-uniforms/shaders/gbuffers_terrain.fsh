#version 120
/* RENDERTARGETS:0,1 */
const vec4 colortex1ClearColor = vec4(0.0);
uniform float veyraNoonFade;
uniform float shadowFade;
uniform int veyraDayTick;
uniform int framemod8;
uniform int veyraExpectedFrameMod;
uniform bool veyraFlag;
uniform float veyraScaled;
uniform ivec2 atlasSize;
uniform float velocity;
uniform vec2 veyraPair;
uniform vec3 veyraTint;
uniform float veyraTintTail;
uniform vec4 veyraQuad;
uniform sampler2D noisetex;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
void main() {
    vec3 noise = texture2D(noisetex, vec2(0.37, 0.61)).rgb;
    bool vectors = noise.r > 0.94 && noise.g < 0.03 && noise.b > 0.79
            && distance(veyraPair, vec2(0.25, 0.5)) < 0.001
            && distance(veyraTint, vec3(0.25, 0.5, 0.75)) < 0.001 && abs(veyraTintTail - 0.75) < 0.001
            && distance(veyraQuad, vec4(0.25, 0.5, 0.75, 1)) < 0.001;
    vec4 probe = vec4(0.25, 0.5, 0.75, 1.0);
    bool matrices = distance(gbufferProjection * gbufferProjectionInverse * probe, probe) < 0.001;
    bool custom = matrices && vectors && veyraNoonFade > 0.99 && shadowFade > 0.99 && veyraDayTick == 7
            && framemod8 == veyraExpectedFrameMod && veyraFlag && abs(veyraScaled - 2.0) < 0.001
            && atlasSize.x >= 16 && atlasSize.y >= 16 && velocity >= 0.0 && velocity < 0.05;
    gl_FragData[0] = vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[1] = custom ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
}
