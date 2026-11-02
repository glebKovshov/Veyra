#version 430
coherent uniform uimage3D voxel_img;
coherent uniform uimage2D marker_img;
void main() {
    gl_Position = ftransform();
    imageAtomicMax(voxel_img, ivec3(0), 5u);
    imageAtomicMax(marker_img, ivec2(0), 7u);
}
