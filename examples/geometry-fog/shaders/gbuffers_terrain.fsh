#version 120

uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;

varying vec2 texcoord;
varying vec4 glcolor;
varying float sphericalVertexDistance;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    float fogFactor = clamp(
        (sphericalVertexDistance - fogStart) / max(fogEnd - fogStart, 0.0001),
        0.0,
        1.0);
    gl_FragColor = vec4(mix(color.rgb, fogColor, fogFactor), color.a);
}
