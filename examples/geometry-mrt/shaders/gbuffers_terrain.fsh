#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;
void main() {
    vec4 scene = texture2D(texture, texcoord) * glcolor;
    if (scene.a < 0.1) discard;
    gl_FragData[0] = scene;
    gl_FragData[1] = vec4(scene.rgb * vec3(0.25, 1.0, 0.25), 1.0);
}
