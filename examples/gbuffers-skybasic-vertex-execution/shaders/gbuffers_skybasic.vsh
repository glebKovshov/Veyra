#version 120

varying vec4 glColor;

void main() {
    gl_Position = ftransform();
    glColor = vec4(gl_Color.rgb * 0.0 + vec3(1.0, 0.0, 1.0), 1.0);
}
