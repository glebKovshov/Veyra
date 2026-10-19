#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D depthtex1;
uniform float viewWidth;
uniform float viewHeight;
void main() {
    vec2 uv = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
    float opaqueDepth = texture2D(depthtex1, uv).r;
    // A real opaque floor lies behind this water surface. Cleared/unbound/water depth
    // must not pass: this tests the separate copied image rather than just a descriptor.
    bool copiedFloor = opaqueDepth > gl_FragCoord.z + 0.00001 && opaqueDepth < 0.99999;
    vec4 signal = copiedFloor ? vec4(0.95, 0.02, 0.8, 1.0) : vec4(0.02, 0.8, 0.05, 1.0);
    gl_FragData[0] = signal;
    gl_FragData[1] = signal;
}
