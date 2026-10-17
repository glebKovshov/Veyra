#version 330 compatibility

uniform sampler2D colortex0;
in vec2 texcoord;

void main() {
    vec4 color = texture2D(colortex0, texcoord);
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    gl_FragColor = vec4(vec3(luminance), color.a);
}
