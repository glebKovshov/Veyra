#version 130

out vec2 texCoord;
flat out vec3 upVec, sunVec;
out vec4 glColor;

uniform mat4 gbufferModelView;

void main() {
    gl_Position = ftransform();
    texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    upVec = normalize(gbufferModelView[1].xyz);
    sunVec = normalize(vec3(0.3, 0.8, 0.2));
    glColor = gl_Color;
}
