#version 120
uniform sampler2D colortex0;
uniform sampler2D colortex1;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec4 blockSignal = texture2D(colortex1, texcoord);
    gl_FragColor = vec4(mix(scene.rgb, blockSignal.rgb, blockSignal.a * 0.65), scene.a);
}
