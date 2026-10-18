#version 120

uniform sampler2D texture;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    float fogStrength = clamp(1.0 - fogEnd / max(fogStart + 256.0, 1.0), 0.0, 0.25);
    gl_FragColor = vec4(mix(color.rgb, fogColor, fogStrength), color.a);
}
