#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    bool validViewPosition = all(lessThan(abs(viewPos), vec3(256.0)))
            && dot(viewPos, viewPos) > 0.0625;
    gl_FragColor = validViewPosition
            ? vec4(1.0, 0.0, 1.0, albedo.a)
            : vec4(0.0, 1.0, 0.0, albedo.a);
}
