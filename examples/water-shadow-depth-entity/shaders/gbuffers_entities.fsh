#version 120
uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
void main(){
    bool found=false;
    for(int y=1;y<=3;y++) for(int x=1;x<=3;x++){
        vec2 uv=vec2(float(x),float(y))*0.25;
        float allDepth=texture2D(shadowtex0,uv).r;
        float opaqueDepth=texture2D(shadowtex1,uv).r;
        if(abs(allDepth-0.25)<0.01 && abs(opaqueDepth-0.75)<0.01) found=true;
    }
    gl_FragColor=found?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
}
