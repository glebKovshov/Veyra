#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor;
void main(){
    bool found=false;
    for(int y=1;y<=3;y++) for(int x=1;x<=3;x++){
        vec2 uv=vec2(float(x),float(y))*0.25;
        vec4 data=texture2D(shadowcolor0,uv);
        vec4 aliasData=texture2D(shadowcolor,uv);
        if(distance(data.rgb,vec3(0.2,0.65,0.1))<0.05 && distance(data,aliasData)<0.01) found=true;
    }
    gl_FragData[0]=found?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
    gl_FragData[1]=gl_FragData[0];
}
