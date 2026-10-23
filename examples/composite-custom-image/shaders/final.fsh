#version 430
uniform usampler2D post_sampler;
void main() {
    uint postComputed=texelFetch(post_sampler,ivec2(0),0).r;
    gl_FragColor=postComputed==43u
            ? vec4(1.0,0.0,1.0,1.0) : vec4(1.0,0.0,0.0,1.0);
}
