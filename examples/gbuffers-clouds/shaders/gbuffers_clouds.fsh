#version 120

varying vec4 color;

void main() {
    gl_FragColor = vec4(color.rgb * vec3(0.45, 0.8, 1.0), color.a);
}
