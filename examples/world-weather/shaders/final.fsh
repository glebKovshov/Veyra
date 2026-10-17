#version 330 compatibility

uniform sampler2D colortex0;
uniform int worldTime;
uniform int worldDay;
uniform float sunAngle;
uniform float rainStrength;
uniform float thunderStrength;
in vec2 texcoord;

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    float daylight = clamp(sin(sunAngle * 6.2831853), 0.0, 1.0);
    float calendarTint = 0.98 + 0.02 * sin(float(worldDay) * 0.1);
    float storm = clamp(rainStrength * 0.25 + thunderStrength * 0.35, 0.0, 0.5);
    color.rgb *= mix(0.28, 1.0, daylight) * calendarTint * (1.0 - storm);
    color.rgb += vec3(float(worldTime) / 24000.0 * 0.005);
    color.rgb = mix(color.rgb, color.rgb * vec3(0.82, 0.9, 1.0), thunderStrength * 0.35);
    gl_FragColor = color;
}
