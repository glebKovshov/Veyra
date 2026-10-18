#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

/* const int colortex2Format = RGBA16F; */
/* RENDERTARGETS: 2 */
void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;
    float highlight = max(max(scene.r, scene.g), scene.b);
    gl_FragColor = vec4(scene * (1.0 + highlight * 2.0), 1.0);
}
