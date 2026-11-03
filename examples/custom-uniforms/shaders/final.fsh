#version 120
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform int veyraDayTick;
uniform int framemod8;
uniform int veyraExpectedFrameMod;
uniform float veyraScaled;
uniform float shadowFade;
uniform float cloudHeight;
uniform ivec2 atlasSize;
uniform ivec2 eyeBrightnessSmooth;
uniform float veyraStill;
uniform vec3 sunPosition;
uniform int worldTime;
uniform int veyraExpectedWorldTime;
uniform ivec3 cameraPositionInt;
uniform float viewWidth;
varying vec2 texcoord;
uniform vec2 veyraPair;
uniform vec3 veyraTint;
uniform float veyraTintTail;
uniform vec4 veyraQuad;
uniform sampler2D noisetex;
void main() {
    // The post pass must receive the same per-frame values as terrain, including ivec2 built-ins.
    vec3 noise = texture2D(noisetex, vec2(0.37, 0.61)).rgb;
    vec4 hdr = texture2D(colortex2, texcoord);
    bool hdrCorrect = (distance(hdr.rgb, vec3(1.0)) < 0.0001
            || distance(hdr.rgb, vec3(1.00390625, 1.00390625, 1.0078125)) < 0.0001) && hdr.a > 0.99;
    bool vectors = noise.r > 0.94 && noise.g < 0.03 && noise.b > 0.79
            && distance(veyraPair, vec2(0.25, 0.5)) < 0.001
            && distance(veyraTint, vec3(0.25, 0.5, 0.75)) < 0.001 && abs(veyraTintTail - 0.75) < 0.001
            && distance(veyraQuad, vec4(0.25, 0.5, 0.75, 1)) < 0.001;
    bool custom = hdrCorrect && abs(cloudHeight - 224.0) < 0.001 && vectors && veyraDayTick == 7 && framemod8 == veyraExpectedFrameMod
            && shadowFade > 0.99 && abs(veyraScaled - 2.0) < 0.001
            && atlasSize.x >= 16 && atlasSize.y >= 16 && eyeBrightnessSmooth.y >= 0 && eyeBrightnessSmooth.y <= 240
            && veyraStill > 0.5
            // Scalars following vec3/ivec3 occupy their fourth std140 word.
            && length(sunPosition) > 50.0 && length(sunPosition) < 150.0 && worldTime == veyraExpectedWorldTime
            && cameraPositionInt.y < 0 && viewWidth >= 320.0;
    gl_FragColor = custom ? texture2D(colortex1, texcoord) : vec4(0.02, 0.8, 0.05, 1.0);
}
