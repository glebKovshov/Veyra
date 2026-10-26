#version 120
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor;
void main(){
    vec2 uv=gl_FragCoord.xy/vec2(viewWidth,viewHeight);
    vec4 data=texture2D(shadowcolor0,uv);
    vec4 aliasData=texture2D(shadowcolor,uv);
    bool found=distance(data.rgb,vec3(0.2,0.65,0.1))<0.05
            && distance(data,aliasData)<0.01;
    gl_FragColor=found?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
}
