#version 120

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < alphaTestRef) {
        discard;
    }
    gl_FragColor = vec4(1.0);
}
