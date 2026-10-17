#version 120

uniform sampler2D texture;
uniform sampler2D lightmap;
uniform float alphaTestRef;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;

void main() {
    vec4 albedo = texture2D(texture, texcoord) * glcolor;
    if (albedo.a < alphaTestRef) discard;
    vec3 litColor = albedo.rgb * texture2D(lightmap, lmcoord).rgb;
    gl_FragColor = vec4(0.0, litColor.g, 0.0, albedo.a);
}
