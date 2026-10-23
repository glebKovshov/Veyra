#version 430
layout(std430, binding=0) readonly buffer VeyraState { uint value; } state;
void main() {
    gl_FragColor=state.value==42u
            ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
