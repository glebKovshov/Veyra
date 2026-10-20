#version 120

varying float dist;

void main() {
    float visible = step(0.01, dist);
    gl_FragColor = vec4(visible, 0.0, visible, 1.0);
}
