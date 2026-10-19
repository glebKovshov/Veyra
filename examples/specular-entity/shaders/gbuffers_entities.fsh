#version 450
uniform sampler2D specular;
uniform sampler2D tex;
varying vec2 texcoord;
void main(){
    vec4 data=texture2D(specular,texcoord);
    vec4 albedo=texture2D(tex,texcoord);
    bool valid=textureSize(specular,0).x>1 && data.r>0.8 && data.g<0.1 && data.b>0.7 && data.a>0.9
        && distance(albedo.rgb,data.rgb)>0.2;
    gl_FragColor=valid?vec4(0.95,0.02,0.8,1.0):vec4(0.02,0.8,0.05,1.0);
}
