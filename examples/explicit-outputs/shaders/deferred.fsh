#version 330

/* RENDERTARGETS: 3,5 */
out vec4 outColor0;
out vec4 outColor1;

void main() {
    outColor0 = vec4(1.0, 0.0, 0.0, 1.0);
    outColor1 = vec4(0.0, 0.0, 1.0, 1.0);
}
