#version 120

varying vec3 veyraTerrainVaryingSmoke;

void main() {
    bool linked = veyraTerrainVaryingSmoke.r > 0.9
            && veyraTerrainVaryingSmoke.g < 0.1
            && veyraTerrainVaryingSmoke.b > 0.9;
    gl_FragColor = linked ? vec4(1.0, 0.0, 1.0, 1.0) : vec4(0.0, 0.0, 0.0, 1.0);
}
