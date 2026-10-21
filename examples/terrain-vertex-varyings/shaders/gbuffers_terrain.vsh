#version 120

varying vec3 veyraTerrainVaryingSmoke;
attribute vec4 at_midBlock;

void main() {
    gl_Position = ftransform();
    // The flat-world grass cubes have corner offsets of +/- half a block.
    // Check the actual extended vertex buffer, including the emission component.
    bool validMidBlock = all(lessThan(abs(abs(at_midBlock.xyz) - vec3(32.0)), vec3(0.01)))
            && at_midBlock.w == 0.0;
    veyraTerrainVaryingSmoke = validMidBlock ? vec3(1.0, 0.0, 1.0) : vec3(0.0);
}
