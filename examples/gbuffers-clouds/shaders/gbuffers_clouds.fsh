#version 120

uniform vec3 fogColor;
varying vec4 color;

void main() {
    gl_FragColor = vec4(mix(color.rgb * vec3(0.45, 0.8, 1.0), fogColor, 0.15), color.a);
}
