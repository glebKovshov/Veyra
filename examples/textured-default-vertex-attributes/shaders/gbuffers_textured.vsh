#version 120

attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;
varying float veyraDefaultAttributes;

void main() {
    gl_Position = ftransform();
    veyraDefaultAttributes = 0.0;
    if (mc_Entity.x == 0.0 && mc_Entity.y == 0.0 && mc_Entity.z == 0.0 && mc_Entity.w == 1.0
            && mc_midTexCoord.x == 0.0 && mc_midTexCoord.y == 0.0 && mc_midTexCoord.z == 0.0
            && mc_midTexCoord.w == 1.0) {
        veyraDefaultAttributes = 1.0;
    }
}
