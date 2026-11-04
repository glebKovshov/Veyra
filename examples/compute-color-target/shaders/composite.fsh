#version 430
uniform sampler2D colortex4;
void main(){
    float computed=texelFetch(colortex4,ivec2(2,0),0).r;
    gl_FragColor=abs(computed-0.75)<0.001 ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
