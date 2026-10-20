#version 120
uniform int currentRenderedItemId;

void main() {
    if (currentRenderedItemId == 42) {
        gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (currentRenderedItemId == 43) {
        gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);
    } else {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
}
