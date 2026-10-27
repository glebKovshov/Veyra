#version 120
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D depthtex0;
varying vec2 texcoord;

void main() {
    vec4 scene = texture2D(colortex0, texcoord);
    vec4 handSignal = texture2D(colortex1, texcoord);
    if (scene.r > 0.8 && scene.b > 0.8 && scene.g < 0.2
            && texture2D(depthtex0, texcoord).r >= 0.56) {
        scene.rgb = vec3(0.0);
    }
    gl_FragColor = vec4(mix(scene.rgb, handSignal.rgb, handSignal.a * 0.65), scene.a);
}
