#version 120
void main(){gl_Position=vec4(vec2(-0.55)+clamp(gl_Vertex.xy*0.5,-0.25,0.25),-1.0,1.0);}
