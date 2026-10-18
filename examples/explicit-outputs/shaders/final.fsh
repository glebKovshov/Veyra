#version 330

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D colortex3;
uniform sampler2D colortex5;
uniform sampler2D colortex7;
in vec2 texcoord;

void main() {
    vec3 color;
    if (texcoord.x < 0.2) {
        vec3 green = texture(colortex2, texcoord).rgb;
        color = green.g > 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 1.0);
    } else if (texcoord.x < 0.4) {
        vec3 blue = texture(colortex7, texcoord).rgb;
        color = blue.b > 0.9 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    } else if (texcoord.x < 0.6) {
        vec3 red = texture(colortex3, texcoord).rgb;
        color = red.r > 0.9 ? vec3(1.0, 1.0, 0.0) : vec3(0.0, 1.0, 1.0);
    } else if (texcoord.x < 0.8) {
        vec3 blue = texture(colortex5, texcoord).rgb;
        color = blue.b > 0.9 ? vec3(1.0, 0.0, 1.0) : vec3(0.0, 1.0, 1.0);
    } else {
        color = texture(colortex0, texcoord).rgb;
    }
    gl_FragColor = vec4(color, 1.0);
}
