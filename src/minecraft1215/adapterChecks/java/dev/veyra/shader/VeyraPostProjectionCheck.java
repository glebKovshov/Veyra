package dev.veyra.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class VeyraPostProjectionCheck {
    public static void run() {
        Matrix4f first = VeyraPostProjection.frameInfo(64, 32);
        var near = first.transformPosition(new Vector3f(0, 0, -0.1F));
        var far = first.transformPosition(new Vector3f(64, 32, -1000));
        assert near.distance(-1, -1, -1) < 0.0001F : near;
        assert far.distance(1, 1, 1) < 0.0001F : far;
        var saved = new Matrix4f(first);
        assert VeyraPostProjection.frameInfo(128, 64) != first;
        VeyraPostProjection.reset();
        assert first.equals(saved) : "Later frames changed a queued projection";
        for (int[] size : new int[][]{{0, 32}, {64, 0}, {-1, 32}, {64, -1}}) {
            try { VeyraPostProjection.frameInfo(size[0], size[1]); throw new AssertionError("Invalid viewport accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        FrameGraphBuilder graph = new FrameGraphBuilder();
        Map<ResourceLocation, ResourceHandle<RenderTarget>> targets = new java.util.HashMap<>();
        CapturingPass pass = new CapturingPass();
        VeyraPostProjection.addToFrame(pass, graph, targets, first);
        assert pass.graph == graph && pass.targets == targets && pass.projection == first;
        assert pass.calls == 1;
        System.out.println("Legacy post projection boundaries, frame ownership and native delegation passed");
    }

    private static final class CapturingPass extends PostPass {
        private FrameGraphBuilder graph;
        private Map<ResourceLocation, ResourceHandle<RenderTarget>> targets;
        private Matrix4f projection;
        private int calls;
        CapturingPass() {
            super(RenderPipeline.builder().withLocation(ResourceLocation.fromNamespaceAndPath("veyra", "projection-check"))
                    .withVertexShader(ResourceLocation.fromNamespaceAndPath("veyra", "projection-check"))
                    .withFragmentShader(ResourceLocation.fromNamespaceAndPath("veyra", "projection-check"))
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES).build(),
                    ResourceLocation.fromNamespaceAndPath("veyra", "projection-check"), List.of());
        }
        @Override public void addToFrame(FrameGraphBuilder graph,
                Map<ResourceLocation, ResourceHandle<RenderTarget>> targets, Matrix4f projection,
                Consumer<RenderPass> uniforms) {
            this.graph = graph;
            this.targets = targets;
            this.projection = projection;
            calls++;
            uniforms.accept(null);
        }
    }
}
