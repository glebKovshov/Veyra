#version 430
coherent uniform uimage3D voxel_img;

void main() {
    gl_Position = ftransform();
    imageAtomicMax(voxel_img, ivec3(0), 5u);
}
