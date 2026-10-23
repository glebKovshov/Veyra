#version 120
uniform sampler2D colortex1;
uniform int veyraDayTick;
uniform int framemod8;
uniform int veyraExpectedFrameMod;
uniform float veyraScaled;
uniform float shadowFade;
uniform ivec2 atlasSize;
uniform ivec2 eyeBrightnessSmooth;
uniform float veyraStill;
uniform vec3 sunPosition;
uniform int worldTime;
uniform int veyraExpectedWorldTime;
uniform ivec3 cameraPositionInt;
uniform float viewWidth;
varying vec2 texcoord;
void main() {
    // The post pass must receive the same per-frame values as terrain, including ivec2 built-ins.
    bool custom = veyraDayTick == 7 && framemod8 == veyraExpectedFrameMod
            && shadowFade > 0.99 && abs(veyraScaled - 2.0) < 0.001
            && atlasSize.x >= 16 && atlasSize.y >= 16 && eyeBrightnessSmooth.y >= 0 && eyeBrightnessSmooth.y <= 240
            && veyraStill > 0.5
            // Scalars following vec3/ivec3 occupy their fourth std140 word.
            && length(sunPosition) > 50.0 && length(sunPosition) < 150.0 && worldTime == veyraExpectedWorldTime
            && cameraPositionInt.y < 0 && viewWidth >= 320.0;
    gl_FragColor = custom ? texture2D(colortex1, texcoord) : vec4(0.02, 0.8, 0.05, 1.0);
}
