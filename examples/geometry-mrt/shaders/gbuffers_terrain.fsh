#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;
void main() {
    vec4 scene = texture2D(texture, texcoord) * glcolor;
    if (scene.a < 0.1) discard;
    gl_FragData[0] = scene;
    gl_FragData[1] = vec4(0.95, 0.02, 0.8, 1.0);
}
