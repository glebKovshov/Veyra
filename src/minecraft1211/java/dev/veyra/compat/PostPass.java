package dev.veyra.compat;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.joml.Matrix4f;

/**
 * Stand-in for the frame-graph post pass Minecraft added in 1.21.2. The draw follows VulkanMod
 * 0.6.7's own post pass: a full-target quad in pixel coordinates through the pass's program.
 */
public class PostPass {
    public static final ResourceLocation MAIN_TARGET_ID = ResourceLocation.withDefaultNamespace("main");

    private final String name;
    private final ShaderInstance shader;
    private final ResourceLocation outputTargetId;
    private final List<Input> inputs = new ArrayList<>();

    public PostPass(String name, ShaderInstance shader, ResourceLocation outputTargetId, List<?> uniforms) {
        this.name = name;
        this.shader = shader;
        this.outputTargetId = outputTargetId;
    }

    public void addInput(Input input) { inputs.add(input); }

    public ShaderInstance getShader() { return shader; }

    public void addToFrame(FrameGraphBuilder graph, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets,
                           Matrix4f projection) {
        FramePass pass = graph.addPass(name);
        for (Input input : inputs) input.addToPass(pass, targets);
        ResourceHandle<RenderTarget> output = targets.computeIfPresent(outputTargetId, (id, handle) -> pass.readsAndWrites(handle));
        if (output == null) throw new IllegalStateException("Missing handle for target " + outputTargetId);
        pass.executes(() -> {
            RenderTarget target = output.get();
            RenderSystem.viewport(0, 0, target.width, target.height);
            for (Input input : inputs) {
                if (input instanceof TargetInput targetInput
                        && targets.get(targetInput.targetId()).get() instanceof MainTarget main) {
                    main.bindRead();
                }
                input.bindTo(shader, targets);
            }
            shader.safeGetUniform("ProjMat").set(projection);
            shader.safeGetUniform("OutSize").set((float) target.width, (float) target.height);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(false);
            VRenderSystem.disableCull();
            VRenderSystem.depthFunc(519);
            VRenderSystem.setPrimitiveTopologyGL(4);
            Renderer.setInvertedViewport(0, 0, target.width, target.height);
            Renderer.resetScissor();
            RenderSystem.setShader(() -> shader);
            shader.apply();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 500.0F);
            builder.addVertex(target.width, 0.0F, 500.0F);
            builder.addVertex(target.width, target.height, 500.0F);
            builder.addVertex(0.0F, target.height, 500.0F);
            BufferUploader.draw(builder.buildOrThrow());
            RenderSystem.depthFunc(515);
            shader.clear();
            target.unbindWrite();
            Renderer.resetViewport();
            VRenderSystem.enableCull();
            for (Input input : inputs) input.cleanup(targets);
        });
    }

    public interface Input {
        void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets);

        void bindTo(ShaderInstance shader, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets);

        default void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { }
    }

    public record TargetInput(String samplerName, ResourceLocation targetId, boolean depthBuffer, boolean bilinear)
            implements Input {
        @Override public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            ResourceHandle<RenderTarget> handle = targets.get(targetId);
            if (handle == null) throw new IllegalStateException("Missing handle for target " + targetId);
            pass.reads(handle);
        }

        @Override public void bindTo(ShaderInstance shader, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            RenderTarget target = targets.get(targetId).get();
            shader.setSampler(samplerName + "Sampler", depthBuffer ? target.getDepthTextureId() : target.getColorTextureId());
            shader.safeGetUniform(samplerName + "Size").set((float) target.width, (float) target.height);
        }
    }

    public record TextureInput(String samplerName, AbstractTexture texture, int width, int height) implements Input {
        @Override public void addToPass(FramePass pass, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) { }

        @Override public void bindTo(ShaderInstance shader, Map<ResourceLocation, ResourceHandle<RenderTarget>> targets) {
            shader.setSampler(samplerName + "Sampler", texture.getId());
            shader.safeGetUniform(samplerName + "Size").set((float) width, (float) height);
        }
    }
}
