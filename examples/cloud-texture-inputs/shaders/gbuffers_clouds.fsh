#version 120

uniform sampler2D texture;
varying vec2 texCoord;
varying vec3 normal;
varying vec3 sunVec, upVec, eastVec;
varying vec4 color;

void main() {
    vec4 cloud = texture2D(texture, texCoord);
    float face = abs(dot(normalize(normal), normalize(upVec)));
    if (cloud.a < 0.05 || face < 0.95) discard;
    float direction = clamp(0.5 + 0.5 * dot(normalize(sunVec), normalize(eastVec)), 0.25, 1.0);
    gl_FragColor = vec4(direction, 0.0, direction, cloud.a * color.a);
}
