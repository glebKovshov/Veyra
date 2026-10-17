#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D colortex7;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec3 greenTint = texture2D(colortex2, texcoord).rgb;
    vec3 blueTint = texture2D(colortex7, texcoord).rgb;
    vec3 tint = mix(greenTint, blueTint, 0.5);
    gl_FragColor = vec4(mix(scene.rgb, tint, 0.35), scene.a);
}
