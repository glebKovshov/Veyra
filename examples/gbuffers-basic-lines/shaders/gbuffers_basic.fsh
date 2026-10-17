#version 120

varying vec4 glcolor;

void main() {
    gl_FragColor = vec4(glcolor.b, glcolor.r, glcolor.g, glcolor.a);
}
