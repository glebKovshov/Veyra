#version 120
// A gold-block slab hangs 30 blocks above the player at noon: it lies between the player and the
// sun, outside the camera view. Iris draws such casters; the map must contain them.
const int shadowMapResolution = 512;
const float shadowDistance = 48.0;

varying vec2 texcoord;

void main() {
    vec4 albedo = texture2D(texture, texcoord);
    if (albedo.a < 0.1) discard;
    // Untinted gold texels are saturated yellow; grass, dirt and bedrock are not.
    bool gold = albedo.r > 0.75 && albedo.g > 0.5 && albedo.r - albedo.b > 0.35;
    gl_FragColor = gold ? vec4(1.0, 0.85, 0.1, 1.0) : vec4(0.0, 0.0, 1.0, 1.0);
}
