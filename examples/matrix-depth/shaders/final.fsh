#version 120

uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
varying vec2 texcoord;

void main() {
    float depth = texture2D(depthtex0, texcoord).r;
    if (depth >= 0.999999) {
        gl_FragColor = vec4(0.05, 0.08, 0.16, 1.0);
        return;
    }

    vec3 ndcPosition = vec3(texcoord * 2.0 - 1.0, depth);
    vec4 viewPosition = gbufferProjectionInverse * vec4(ndcPosition, 1.0);
    viewPosition /= viewPosition.w;
    vec3 playerPosition = (gbufferModelViewInverse * vec4(viewPosition.xyz, 1.0)).xyz;

    float distanceColor = clamp(length(playerPosition) / 96.0, 0.0, 1.0);
    vec3 nearColor = vec3(0.02, 0.85, 0.65);
    vec3 farColor = vec3(0.95, 0.14, 0.06);
    gl_FragColor = vec4(mix(nearColor, farColor, distanceColor), 1.0);
}
