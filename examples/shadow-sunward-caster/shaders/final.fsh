#version 120
uniform sampler2D colortex0;
uniform sampler2D shadowcolor0;
varying vec2 texcoord;

void main() {
    // Map the whole shadow map onto the frame and mark the gold caster's texels.
    vec3 shadowColor = texture2D(shadowcolor0, texcoord).rgb;
    bool gold = shadowColor.r > 0.9 && shadowColor.g > 0.75 && shadowColor.b < 0.2;
    gl_FragColor = gold ? vec4(0.95, 0.02, 0.8, 1.0) : texture2D(colortex0, texcoord);
}
