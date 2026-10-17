#version 120

uniform sampler2D colortex0;
varying vec2 texcoord;

/* RENDERTARGETS: 0,2,7 */
void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    gl_FragData[0] = scene;
    gl_FragData[1] = vec4(scene.rgb * vec3(0.2, 1.0, 0.2), scene.a);
    gl_FragData[2] = vec4(scene.rgb * vec3(0.2, 0.3, 1.0), scene.a);
}
