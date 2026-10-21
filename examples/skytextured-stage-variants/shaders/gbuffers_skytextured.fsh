#version 130

uniform int renderStage;
varying vec2 texCoord;

void main() {
    if (renderStage == MC_RENDER_STAGE_SUN) {
        gl_FragColor = vec4(1.0, 0.0, 1.0, 1.0);
    } else if (renderStage == MC_RENDER_STAGE_MOON) {
        gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);
    } else {
        gl_FragColor = vec4(0.0, 0.0, 1.0, 1.0);
    }
}
