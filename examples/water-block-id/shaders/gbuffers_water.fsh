#version 120

flat in int mat;
flat in vec2 mc_Entity;
varying float dist;
varying vec3 sunVec, upVec, eastVec;
varying vec4 vTexCoord, vTexCoordAM;

void main() {
    bool validDistance = dist > 0.0 && dist < 256.0;
    bool validDirections = dot(sunVec, sunVec) > 0.9 && dot(sunVec, sunVec) < 1.1
            && abs(dot(upVec, upVec) - 1.0) < 0.02
            && abs(dot(eastVec, eastVec) - 1.0) < 0.02;
    bool validPackUv = all(lessThan(abs(vTexCoord), vec4(256.0)))
            && all(lessThan(abs(vTexCoordAM), vec4(256.0)))
            && all(greaterThanEqual(vTexCoordAM.pq, vec2(0.0)));
    gl_FragColor = mat == 27102 && mc_Entity.y > 0.5 && validDistance && validDirections && validPackUv
            ? vec4(1.0, 0.0, 1.0, 1.0)
            : vec4(0.0, 0.0, 0.0, 1.0);
}
