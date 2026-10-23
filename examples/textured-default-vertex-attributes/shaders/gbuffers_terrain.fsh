#version 120

// Keeps terrain opaque behind the world border; without it Iris' fallback
// chain would route terrain through gbuffers_textured and discard it.
void main() {
    gl_FragColor = vec4(0.15, 0.3, 0.1, 1.0);
}
