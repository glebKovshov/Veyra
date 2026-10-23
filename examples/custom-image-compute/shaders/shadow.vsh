#version 430
coherent uniform uimage3D voxel_img;
uniform sampler2D tex;
uniform sampler2D lightmap;
void main() {
    gl_Position = ftransform();
    // Atomic writes are well-defined even when many terrain vertices touch this diagnostic voxel.
    vec4 albedo = texture2DLod(tex, gl_MultiTexCoord0.xy, 0.0);
    vec4 light = texture2DLod(lightmap, vec2(0.5), 0.0);
    if (albedo.a > 0.0 && light.r > 0.0) imageAtomicMax(voxel_img, ivec3(0), 5u);
}
