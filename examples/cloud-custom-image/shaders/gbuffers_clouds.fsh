#version 430
uniform usampler3D voxel_sampler;
uniform int renderStage;
void main() {
  uint value = texelFetch(voxel_sampler, ivec3(0), 0).r;
  bool cloudStage = renderStage == MC_RENDER_STAGE_CLOUDS;
  gl_FragColor = cloudStage && value == 5u
      ? vec4(1.0, 0.0, 1.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
}
