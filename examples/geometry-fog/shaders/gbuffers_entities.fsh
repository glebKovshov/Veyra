#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;
varying float sphericalVertexDistance;

void main() {
    vec4 color = texture2D(texture, texcoord) * glcolor;
    float fogFactor = clamp(
        (sphericalVertexDistance - gl_Fog.start) * gl_Fog.scale,
        0.0,
        1.0);
    gl_FragColor = vec4(mix(color.rgb, gl_Fog.color.rgb, fogFactor), color.a);
}
