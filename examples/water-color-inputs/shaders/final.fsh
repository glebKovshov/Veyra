#version 120
uniform sampler2D colortex1;
uniform sampler2D colortex5;
const bool colortex5MipmapEnabled = true;
varying vec2 texcoord;
void main() {
    vec4 mip = texture2DLod(colortex5, texcoord, 1.0);
    bool correct = distance(mip.rgb, vec3(0.5, 0.0, 0.5)) < 0.02 && mip.a > 0.99;
    gl_FragColor = correct ? texture2D(colortex1, texcoord) : vec4(0.02, 0.8, 0.05, 1.0);
}
