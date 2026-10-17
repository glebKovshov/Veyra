#version 120

uniform sampler2D colortex0;
uniform float blindness;
uniform float nightVision;
uniform float darknessFactor;

varying vec2 texcoord;

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    float effectStrength = clamp(max(max(blindness, nightVision), darknessFactor), 0.0, 1.0);
    vec3 effectTint = mix(color.rgb * vec3(0.12, 0.12, 0.16), color.rgb * vec3(0.55, 1.0, 0.55), nightVision);
    color.rgb = mix(color.rgb, effectTint, effectStrength);
    gl_FragColor = color;
}
