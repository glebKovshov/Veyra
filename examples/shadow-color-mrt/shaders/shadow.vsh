#version 120
attribute vec4 at_midBlock;
varying vec4 color;
uniform int renderStage;
void main() {
    gl_Position = ftransform();
    bool validStage = renderStage == MC_RENDER_STAGE_TERRAIN_SOLID
            || renderStage == MC_RENDER_STAGE_TERRAIN_CUTOUT;
    bool validMidBlock = all(lessThan(abs(abs(at_midBlock.xyz) - vec3(32.0)), vec3(0.01)))
            && at_midBlock.w == 0.0 && validStage;
    color = validMidBlock ? vec4(0.1, 0.2, 0.9, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
}
