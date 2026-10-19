#version 120

varying vec2 lmCoord;
varying vec4 color;
uniform sampler2D lightmap;

void main() {
    vec3 light = texture2D(lightmap, lmCoord).rgb;
    gl_FragColor = vec4(color.rgb * light, color.a);
}
