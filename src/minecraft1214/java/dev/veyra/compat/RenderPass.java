package dev.veyra.compat;

import org.joml.Matrix4f;

/** The subset of the 1.21.5 render pass API that Veyra uses to bind inputs of the current draw. */
public interface RenderPass extends AutoCloseable {
    RenderPipeline getPipeline();
    void bindSampler(String name, GpuTexture texture);
    void setUniform(String name, float... values);
    void setUniform(String name, int... values);
    void setUniform(String name, Matrix4f matrix);
    @Override void close();
}
