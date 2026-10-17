#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex2;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec3 accumulated = texture2D(colortex2, texcoord).rgb;
    gl_FragColor = vec4(mix(scene.rgb, vec3(1.0), accumulated), scene.a);
}
