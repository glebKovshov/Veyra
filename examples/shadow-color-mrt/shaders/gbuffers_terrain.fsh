#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D shadowcolor1;
void main() {
    bool found = false;
    for (int y = 1; y <= 3; y++) for (int x = 1; x <= 3; x++) {
        vec2 uv = vec2(float(x), float(y)) * 0.25;
        vec4 data = texture2D(shadowcolor1, uv);
        if (distance(data.rgb, vec3(0.1, 0.2, 0.9)) < 0.05) found = true;
    }
    vec4 signal = found ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
