#version 120

varying vec4 glcolor;

void main() {
    gl_FragColor = vec4(1.0, 0.65, 0.0, glcolor.a);
}
