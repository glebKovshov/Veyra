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
void main() {
    bool custom = veyraNoonFade > 0.99 && shadowFade > 0.99 && veyraDayTick == 7
            && framemod8 == veyraExpectedFrameMod && veyraFlag && abs(veyraScaled - 2.0) < 0.001
            && atlasSize.x >= 16 && atlasSize.y >= 16 && velocity >= 0.0 && velocity < 0.05;
    gl_FragData[0] = vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[1] = custom ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
}
