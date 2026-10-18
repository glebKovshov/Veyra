#version 120

uniform sampler2D tex;
varying vec2 texcoord;

void main() {
    ivec2 atlasSize = textureSize(tex, 0);
    vec4 atlasColor = texture2D(tex, texcoord);
    gl_FragColor = atlasColor * float(atlasSize.x > 0);
}
