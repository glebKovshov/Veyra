package dev.veyra.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

/**
 * Stand-in for the pipeline description Minecraft introduced in 1.21.5. Before that version a draw
 * is described by a shader program plus global render state, so the state fields here only classify
 * a pipeline; VulkanMod still takes blend, depth and cull state from the render system at draw time.
 */
public final class RenderPipeline implements ExtendedRenderPipeline {
    private final ResourceLocation location;
    private final ResourceLocation vertexShader;
    private final ResourceLocation fragmentShader;
    private final ShaderDefines defines;
    private final List<String> samplers;
    private final List<UniformDescription> uniforms;
    private final Optional<BlendFunction> blend;
    private final DepthTestFunction depthTest;
    private final PolygonMode polygonMode;
    private final boolean cull;
    private final boolean writeColor;
    private final boolean writeAlpha;
    private final boolean writeDepth;
    private final LogicOp colorLogic;
    private final VertexFormat vertexFormat;
    private final VertexFormat.Mode vertexFormatMode;
    private final float depthBiasScaleFactor;
    private final float depthBiasConstant;
    private GraphicsPipeline pipeline;
    private EGlProgram program;

    private RenderPipeline(Builder builder) {
        location = builder.location;
        vertexShader = builder.vertexShader;
        fragmentShader = builder.fragmentShader;
        ShaderDefines.Builder shaderDefines = ShaderDefines.builder();
        builder.defines.values().forEach(define -> define.accept(shaderDefines));
        defines = shaderDefines.build();
        samplers = List.copyOf(builder.samplers);
        uniforms = List.copyOf(builder.uniforms);
        blend = builder.blend;
        depthTest = builder.depthTest;
        polygonMode = builder.polygonMode;
        cull = builder.cull;
        writeColor = builder.writeColor;
        writeAlpha = builder.writeAlpha;
        writeDepth = builder.writeDepth;
        colorLogic = builder.colorLogic;
        vertexFormat = builder.vertexFormat;
        vertexFormatMode = builder.vertexFormatMode;
        depthBiasScaleFactor = builder.depthBiasScaleFactor;
        depthBiasConstant = builder.depthBiasConstant;
    }

    public ResourceLocation getLocation() { return location; }
    public ResourceLocation getVertexShader() { return vertexShader; }
    public ResourceLocation getFragmentShader() { return fragmentShader; }
    public ShaderDefines getShaderDefines() { return defines; }
    public List<String> getSamplers() { return samplers; }
    public List<UniformDescription> getUniforms() { return uniforms; }
    public Optional<BlendFunction> getBlendFunction() { return blend; }
    public DepthTestFunction getDepthTestFunction() { return depthTest; }
    public PolygonMode getPolygonMode() { return polygonMode; }
    public boolean isCull() { return cull; }
    public boolean isWriteColor() { return writeColor; }
    public boolean isWriteAlpha() { return writeAlpha; }
    public boolean isWriteDepth() { return writeDepth; }
    public LogicOp getColorLogic() { return colorLogic; }
    public VertexFormat getVertexFormat() { return vertexFormat; }
    public VertexFormat.Mode getVertexFormatMode() { return vertexFormatMode; }
    public float getDepthBiasScaleFactor() { return depthBiasScaleFactor; }
    public float getDepthBiasConstant() { return depthBiasConstant; }

    @Override public GraphicsPipeline getPipeline() { return pipeline; }
    @Override public void setPipeline(GraphicsPipeline pipeline) { this.pipeline = pipeline; }
    @Override public EGlProgram getProgram() { return program; }
    @Override public void setProgram(EGlProgram program) {
        if (this.program != null && this.program != program) LegacyDraws.unregister(this.program);
        this.program = program;
    }

    @Override public String toString() { return location.toString(); }

    public static Builder builder(Snippet... snippets) {
        Builder builder = new Builder();
        for (Snippet snippet : snippets) snippet.apply(builder);
        return builder;
    }

    public record UniformDescription(String name, UniformType type) { }

    /** A reusable partial pipeline description. */
    @FunctionalInterface public interface Snippet {
        void apply(Builder builder);
    }

    public static final class Builder {
        private ResourceLocation location;
        private ResourceLocation vertexShader;
        private ResourceLocation fragmentShader;
        // Keyed by name: a later definition replaces an earlier one, such as one copied from a program config.
        private final java.util.Map<String, java.util.function.Consumer<ShaderDefines.Builder>> defines =
                new java.util.LinkedHashMap<>();
        private final List<String> samplers = new ArrayList<>();
        private final List<UniformDescription> uniforms = new ArrayList<>();
        private Optional<BlendFunction> blend = Optional.empty();
        private DepthTestFunction depthTest = DepthTestFunction.LEQUAL_DEPTH_TEST;
        private PolygonMode polygonMode = PolygonMode.FILL;
        private boolean cull = true;
        private boolean writeColor = true;
        private boolean writeAlpha = true;
        private boolean writeDepth = true;
        private LogicOp colorLogic = LogicOp.NONE;
        private VertexFormat vertexFormat;
        private VertexFormat.Mode vertexFormatMode;
        private float depthBiasScaleFactor;
        private float depthBiasConstant;

        private Builder() { }

        public Builder withLocation(ResourceLocation location) { this.location = location; return this; }
        public Builder withVertexShader(ResourceLocation shader) { vertexShader = shader; return this; }
        public Builder withFragmentShader(ResourceLocation shader) { fragmentShader = shader; return this; }
        public Builder withShaderDefine(String flag) { defines.put(flag, target -> target.define(flag)); return this; }
        public Builder withShaderDefine(String name, int value) { return withShaderDefine(name, Integer.toString(value)); }
        public Builder withShaderDefine(String name, String value) { defines.put(name, target -> target.define(name, value)); return this; }
        public Builder withShaderDefine(String name, float value) { defines.put(name, target -> target.define(name, value)); return this; }
        public Builder withSampler(String sampler) { samplers.add(sampler); return this; }
        public Builder withUniform(String name, UniformType type) {
            uniforms.add(new UniformDescription(name, type));
            return this;
        }
        public Builder withBlend(BlendFunction function) { blend = Optional.of(function); return this; }
        public Builder withoutBlend() { blend = Optional.empty(); return this; }
        public Builder withDepthTestFunction(DepthTestFunction function) { depthTest = function; return this; }
        public Builder withPolygonMode(PolygonMode mode) { polygonMode = mode; return this; }
        public Builder withCull(boolean cull) { this.cull = cull; return this; }
        public Builder withColorWrite(boolean color) { return withColorWrite(color, color); }
        public Builder withColorWrite(boolean color, boolean alpha) {
            writeColor = color;
            writeAlpha = alpha;
            return this;
        }
        public Builder withDepthWrite(boolean depth) { writeDepth = depth; return this; }
        public Builder withColorLogic(LogicOp logic) { colorLogic = logic; return this; }
        public Builder withVertexFormat(VertexFormat format, VertexFormat.Mode mode) {
            vertexFormat = format;
            vertexFormatMode = mode;
            return this;
        }
        public Builder withDepthBias(float scaleFactor, float constant) {
            depthBiasScaleFactor = scaleFactor;
            depthBiasConstant = constant;
            return this;
        }

        public RenderPipeline build() {
            if (location == null || vertexShader == null || fragmentShader == null || vertexFormat == null) {
                throw new IllegalStateException("Pipeline needs a location, both shaders and a vertex format.");
            }
            return new RenderPipeline(this);
        }
    }
}
