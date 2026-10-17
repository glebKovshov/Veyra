#version 330 compatibility

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;
varying vec4 lightMapColor;
varying vec4 overlayColor;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor * lightMapColor;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    gl_FragColor = vec4(color.b, color.r, color.g, color.a);
}
