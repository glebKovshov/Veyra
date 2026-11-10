package dev.veyra.compat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;

/** Stand-in for the 1.21.5 pipeline registry, backed by the vanilla render types. */
public final class RenderPipelines {
    /** A full-target quad without depth, culling or blending. */
    public static final RenderPipeline.Snippet POST_PROCESSING_SNIPPET = builder -> builder
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false);

    private RenderPipelines() { }

    public static List<RenderPipeline> getStaticPipelines() { return VanillaPipelines.all(); }

    /** The sky disc pipeline; resolved on use because program configs change with resource reloads. */
    public static RenderPipeline sky() { return VanillaRenderTypes.sky(); }
}
