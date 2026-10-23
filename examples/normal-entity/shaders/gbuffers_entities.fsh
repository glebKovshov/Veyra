#version 450
uniform sampler2D normals;
uniform sampler2D tex;
varying vec2 texcoord;
void main(){
    vec4 data=texture2D(normals,texcoord);
    bool valid=textureSize(normals,0).x>1 && data.r>0.95
        && data.g>0.45 && data.g<0.55 && data.b>0.45 && data.b<0.55;
    gl_FragColor=valid?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
}
