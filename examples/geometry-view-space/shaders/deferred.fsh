#version 120
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec4 encoded = texture2D(colortex1, texcoord);
    float depth = texture2D(depthtex0, texcoord).r;
    vec4 reconstructed = gbufferProjectionInverse * vec4(texcoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 viewPos = reconstructed.xyz / reconstructed.w;
    bool matches = length(viewPos - (encoded.xyz - 0.5) * 100.0) < 1.0;
    bool validSkyRay = (gbufferProjectionInverse * vec4(0.0, 0.0, 1.0, 1.0)).w > 0.0;
    gl_FragColor = encoded.a > 0.5 && depth < 1.0 && (!matches || !validSkyRay)
            ? vec4(0.0, 1.0, 0.0, 1.0) : scene;
}
