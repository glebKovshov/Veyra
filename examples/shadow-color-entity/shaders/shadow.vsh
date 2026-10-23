#version 120
flat varying int mat;
void main(){
    gl_Position=ftransform();
    mat=1;
}
