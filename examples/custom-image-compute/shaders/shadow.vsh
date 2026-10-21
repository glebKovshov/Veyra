#version 430
coherent uniform uimage3D voxel_img;
void main() {
    gl_Position = ftransform();
    // Atomic writes are well-defined even when many terrain vertices touch this diagnostic voxel.
    imageAtomicMax(voxel_img, ivec3(0), 5u);
}
