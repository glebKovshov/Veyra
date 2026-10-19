#version 120

uniform sampler2D colortex0;
uniform sampler2D noisetex;

void main() {
    vec3 sceneColor = texture2D(colortex0, vec2(0.5)).rgb;
    float noise = texture2DLod(noisetex, vec2(0.5), 0.0).r;
    gl_FragColor = noise >= 0.0 && sceneColor.b > sceneColor.r + 0.1
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 0.8, 0.05, 1.0);
}
