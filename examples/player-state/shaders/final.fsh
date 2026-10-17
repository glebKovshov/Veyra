#version 330 compatibility

uniform sampler2D colortex0;
uniform float eyeAltitude;
uniform float screenBrightness;
uniform int isEyeInWater;
uniform bool isSpectator;
uniform bool is_sneaking;
uniform bool is_sprinting;
uniform bool is_on_ground;
uniform bool is_hurt;
uniform bool is_invisible;
uniform bool is_burning;
in vec2 texcoord;

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    if (isEyeInWater == 1) color.rgb *= vec3(0.65, 0.82, 1.0);
    if (isEyeInWater == 2) color.rgb *= vec3(1.0, 0.48, 0.2);
    if (isSpectator) color.rgb = mix(color.rgb, vec3(0.4, 0.3, 0.8), 0.15);
    if (is_sneaking) color.rgb *= 0.97;
    if (is_sprinting && is_on_ground) color.rgb *= 1.02;
    if (is_hurt) color.rgb = mix(color.rgb, vec3(1.0, 0.1, 0.1), 0.2);
    if (is_invisible) color.rgb *= 0.95;
    if (is_burning) color.rgb = mix(color.rgb, vec3(1.0, 0.45, 0.1), 0.12);
    color.rgb *= mix(0.5, 1.0, screenBrightness);
    color.rgb += vec3(clamp(eyeAltitude / 320.0, 0.0, 1.0) * 0.005);
    gl_FragColor = color;
}
