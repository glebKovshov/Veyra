#version 120
/* RENDERTARGETS:5 */
uniform sampler2D noisetex; // deliberately unused header
uniform sampler2D shadowtex0;
uniform sampler2DShadow shadowtex1;
uniform sampler2D shadowcolor0;
uniform sampler2D shadowcolor;
uniform sampler2D colortex4;
float compareDepth(sampler2DShadow s,vec3 uv){return texture(s,uv);}
void main(){bool found=false;
for(int y=1;y<=3;y++) for(int x=1;x<=3;x++){
vec2 uv=vec2(float(x),float(y))*0.25;
float allDepth=texture2D(shadowtex0,uv).r;
bool opaqueValid=compareDepth(shadowtex1,vec3(uv,0.7))>0.9 && texture(shadowtex1,vec3(uv,0.8))<0.1;
vec4 color=texture2D(shadowcolor0,uv);
if(abs(allDepth-0.25)<0.01 && opaqueValid && distance(color.rgb,vec3(0.2,0.65,0.1))<0.05 && distance(color,texture2D(shadowcolor,uv))<0.01)found=true;
}
found=found && distance(texture2D(colortex4,vec2(0.5)).rgb,vec3(0.2,0.65,0.1))<0.05;
gl_FragColor=found?vec4(0.2,0.65,0.1,1.0):vec4(0.0);
}
