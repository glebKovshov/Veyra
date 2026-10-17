#version 120

uniform sampler2D shadowtex0;
varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec4 shadowPos;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    vec4 light = texture2D(lightmap, lmcoord);
    vec3 shadowNdc = shadowPos.xyz / shadowPos.w;
    vec2 shadowUv = vec2(shadowNdc.x * 0.5 + 0.5, 0.5 - shadowNdc.y * 0.5);
    float visibility = 1.0;
    if (shadowUv.x >= 0.0 && shadowUv.x <= 1.0
            && shadowUv.y >= 0.0 && shadowUv.y <= 1.0
            && shadowNdc.z >= 0.0 && shadowNdc.z <= 1.0) {
        float storedDepth = texture2D(shadowtex0, shadowUv).r;
        visibility = shadowNdc.z <= storedDepth + 0.001 ? 1.0 : 0.45;
    }
    gl_FragColor = vec4(albedo.rgb * light.rgb * visibility, albedo.a);
}
