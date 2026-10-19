#version 120
const int shadowMapResolution=256;
const float shadowDistance=32.0;
void main(){gl_FragDepth=0.75;gl_FragColor=vec4(0.2,0.65,0.1,1.0);}
