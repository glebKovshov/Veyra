#version 330

uniform sampler2D colortex0;
in vec2 texcoord;

/* RENDERTARGETS: 0,2,7 */
layout(location = 0) out vec4 sceneColor;
layout(location = 1) out vec4 greenBuffer;
layout(location = 2) out vec4 blueBuffer;

void main() {
    sceneColor = texture(colortex0, texcoord);
    greenBuffer = vec4(0.0, 1.0, 0.0, 1.0);
    blueBuffer = vec4(0.0, 0.0, 1.0, 1.0);
}
