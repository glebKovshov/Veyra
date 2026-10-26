#version 120
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor;
uniform sampler2D shadowtex0;
void main(){
    vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    vec4 data=texture2D(shadowcolor0,uv);
    vec4 aliasData=texture2D(shadowcolor,uv);
    bool found=all(greaterThan(uv,vec2(0.05))) && all(lessThan(uv,vec2(0.4)))
            && distance(data.rgb,vec3(0.2,0.65,0.1))<0.05
            && distance(data,aliasData)<0.01
            && texture2D(shadowtex0,uv).r<0.00001;
    gl_FragColor=found?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
}
