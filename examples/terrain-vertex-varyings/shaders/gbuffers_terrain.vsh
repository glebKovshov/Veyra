#version 120

varying vec3 veyraTerrainVaryingSmoke;

void main() {
    gl_Position = ftransform();
    veyraTerrainVaryingSmoke = vec3(1.0, 0.0, 1.0);
}
