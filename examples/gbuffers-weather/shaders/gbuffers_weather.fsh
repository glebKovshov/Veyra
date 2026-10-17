#version 120

uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 weather = texture2D(texture, texcoord) * glcolor;
    gl_FragColor = vec4(weather.a * 0.15, weather.a, weather.a * 0.25, weather.a);
}
