#version 120
const int shadowMapResolution=256;
const float shadowDistance=32.0;
flat varying int mat;
void main(){gl_FragColor=mat==1?vec4(0.1,0.2,0.8,1.0):vec4(0.0,0.0,0.0,1.0);}
