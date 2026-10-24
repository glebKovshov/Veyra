#version 120
/* RENDERTARGETS:0,1 */
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor;
void main(){
    // Cover the shadow map across the fixed 640x360 smoke frame instead of assuming one light angle.
    vec2 uv=gl_FragCoord.xy/vec2(640.0,360.0);
    vec4 data=texture2D(shadowcolor0,uv);
    vec4 aliasData=texture2D(shadowcolor,uv);
    bool found=distance(data.rgb,vec3(0.1,0.2,0.9))<0.05 && distance(data,aliasData)<0.01;
    gl_FragData[0]=found?vec4(0.95,0.02,0.8,1.0):vec4(1.0,0.0,0.0,1.0);
    gl_FragData[1]=gl_FragData[0];
}
