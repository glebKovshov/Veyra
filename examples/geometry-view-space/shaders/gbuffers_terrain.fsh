#version 120

uniform sampler2D texture;

varying vec2 texcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying float dist;
varying vec3 normal, binormal, tangent;
varying vec3 viewVector;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a <= alphaTestRef) {
        discard;
    }

    bool validViewPosition = all(lessThan(abs(viewPos), vec3(256.0)))
            && dot(viewPos, viewPos) > 0.0625;
    bool validDistance = dist > 0.0 && dist < 256.0;
    bool orthonormalTangentFrame = abs(dot(normal, tangent)) < 0.02
            && abs(dot(normal, binormal)) < 0.02
            && abs(dot(tangent, binormal)) < 0.02
            && abs(dot(normal, normal) - 1.0) < 0.02
            && abs(dot(tangent, tangent) - 1.0) < 0.02
            && abs(dot(binormal, binormal) - 1.0) < 0.02;
    bool viewVectorKeepsLength = abs(dot(viewVector, viewVector) - dot(viewPos, viewPos))
            < max(0.05, dot(viewPos, viewPos) * 0.02);
    gl_FragColor = validViewPosition && validDistance && orthonormalTangentFrame && viewVectorKeepsLength
            ? vec4(1.0, 0.0, 1.0, albedo.a)
            : vec4(0.0, 1.0, 0.0, albedo.a);
}
