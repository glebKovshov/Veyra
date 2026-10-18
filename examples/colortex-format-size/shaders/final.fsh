#version 120

uniform sampler2D colortex0;
uniform sampler2D colortex2;
varying vec2 texcoord;

void main() {
    vec3 scene = texture2D(colortex0, texcoord).rgb;
    vec3 highlight = texture2D(colortex2, texcoord).rgb;
    vec3 color = scene + highlight * 0.2;
    color = color / (vec3(1.0) + color);
    gl_FragColor = vec4(color, 1.0);
}
