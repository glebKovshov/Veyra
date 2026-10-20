#version 130

/* DRAWBUFFERS:06 */

in vec2 texCoord;
flat in vec3 upVec, sunVec;
in vec4 glColor;

void main() {
    float signal = clamp(0.75 + 0.1 * abs(upVec.y) + 0.05 * abs(sunVec.x)
            + 0.05 * texCoord.x + 0.05 * glColor.r, 0.0, 1.0);
    gl_FragData[0] = vec4(0.0, signal, 0.0, 1.0);
    gl_FragData[1] = vec4(1.0, 0.0, 1.0, 1.0);
}
