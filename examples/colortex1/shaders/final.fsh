#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex1;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec3 inverted = texture2D(colortex1, texcoord).rgb;
    gl_FragColor = vec4(mix(scene.rgb, inverted, 0.35), scene.a);
}
