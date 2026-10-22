#version 120
varying vec4 color;
uniform int renderStage;
void main() {
    gl_Position = ftransform();
    bool validStage = renderStage == MC_RENDER_STAGE_TERRAIN_SOLID
            || renderStage == MC_RENDER_STAGE_TERRAIN_CUTOUT;
    color = validStage ? vec4(0.1, 0.2, 0.9, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
}
