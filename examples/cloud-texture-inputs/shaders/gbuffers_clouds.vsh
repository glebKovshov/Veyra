#version 120

varying vec2 texCoord;
varying vec3 normal;
varying vec3 sunVec, upVec, eastVec;
varying vec4 color;

uniform float timeAngle;
uniform mat4 gbufferModelView;
const float sunPathRotation = 0.0;

void main() {
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    color = gl_Color;
    normal = normalize(gl_NormalMatrix * gl_Normal);
    sunVec = normalize((gbufferModelView * vec4(vec3(timeAngle), 1.0)).xyz);
    upVec = normalize(gbufferModelView[1].xyz);
    eastVec = normalize(gbufferModelView[0].xyz);
    gl_Position = ftransform();
}
