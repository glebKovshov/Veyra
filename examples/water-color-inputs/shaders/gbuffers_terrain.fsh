#version 120
/* RENDERTARGETS:0,4,5 */
void main() {
    gl_FragData[0] = vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[1] = vec4(1.0, 0.0, 0.0, 1.0);
    // Deferred must replace this green value on the other ping-pong side.
    gl_FragData[2] = vec4(0.0, 1.0, 0.0, 1.0);
}
