package dev.veyra.vulkan;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.Uniforms;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import net.vulkanmod.vulkan.util.MappedBuffer;

/** Uses native UBOs, push constants and shader compilation, with explicit descriptor bindings. */
public final class LegacyPipelineBuilder extends Pipeline.Builder {
    private final String name;
    private final List<UBO> blocks = new ArrayList<>();
    private final List<ImageDescriptor> images = new ArrayList<>();
    private String vertexSource, fragmentSource;
    private boolean configured;
    private Function<Uniform.Info, Supplier<MappedBuffer>> supplierGetter;

    public LegacyPipelineBuilder(VertexFormat format, String name) {
        super(format, name);
        this.name = name;
    }

    public void applyConfig(LegacyPipelineConfig config) {
        if (configured) throw new IllegalStateException("Pipeline already configured");
        var prepared = new ArrayList<UBO>();
        for (var block : config.ubs) {
            var fields = new ArrayList<Uniform.Info>();
            for (var field : block.fields) {
                var info = Uniform.createUniformInfo(LegacyPipelineConfig.Field.nativeType(field.type()), nativeSupplierName(field.name()));
                if (field.type().equals("ivec2") || field.type().equals("ivec3")) {
                    // Native vec fields copy bytes unchanged, so integer vectors retain their bit patterns.
                    info.setBufferSupplier(supplierGetter == null ? Uniforms.getUniformSupplier(field.type(), field.name())
                            : supplierGetter.apply(info));
                } else info.setupSupplier();
                if (!info.hasSupplier() && supplierGetter != null) info.setBufferSupplier(supplierGetter.apply(info));
                if (!info.hasSupplier()) throw new IllegalStateException("No uniform supplier for " + field.name());
                fields.add(info);
            }
            var layout = new net.vulkanmod.vulkan.shader.layout.AlignedStruct.Builder();
            fields.forEach(layout::addUniformInfo);
            prepared.add(layout.buildUBO(block.binding, block.stage));
        }
        JsonObject constants = new JsonObject();
        constants.add("PushConstants", config.pushConstantsInfo.deepCopy());
        super.parseBindings(constants);
        blocks.addAll(prepared);
        for (var image : config.imageDescriptors)
            images.add(new ImageDescriptor(image.binding(), image.type(), image.name(), image.imageIdx(),
                    image.type().equals("image2D")));
        super.setUniforms(blocks, images);
        configured = true;
    }

    private static String nativeSupplierName(String name) {
        // 1.21.5 exposes one effective fog range instead of separate environment/distance ranges.
        return switch (name) {
            case "FogEnvironmentalStart", "FogRenderDistanceStart" -> "FogStart";
            case "FogEnvironmentalEnd", "FogRenderDistanceEnd", "FogSkyEnd", "FogCloudsEnd" -> "FogEnd";
            default -> name;
        };
    }

    @Override public void setUniformSupplierGetter(Function<Uniform.Info, Supplier<MappedBuffer>> getter) {
        super.setUniformSupplierGetter(getter);
        supplierGetter = getter;
    }

    public List<UBO> getUBOs() { return List.copyOf(blocks); }
    public List<ImageDescriptor> getImageDescriptors() { return List.copyOf(images); }
    public void addUBO(UBO block) { requireFreeBinding(block.getBinding()); blocks.add(block); }
    public void addImageDescriptor(ImageDescriptor image) { requireFreeBinding(image.getBinding()); images.add(image); }
    private void requireFreeBinding(int binding) {
        if (binding < 0 || blocks.stream().anyMatch(block -> block.getBinding() == binding)
                || images.stream().anyMatch(image -> image.getBinding() == binding))
            throw new IllegalArgumentException("Invalid or duplicate pipeline binding " + binding);
    }
    public void setShaderSrc(SPIRVUtils.ShaderKind kind, String source) {
        Objects.requireNonNull(source, "source");
        switch (kind) {
            case VERTEX_SHADER -> vertexSource = source;
            case FRAGMENT_SHADER -> fragmentSource = source;
            default -> throw new IllegalArgumentException("Not a graphics shader: " + kind);
        }
    }
    @Override public GraphicsPipeline createGraphicsPipeline() {
        if (!configured) throw new IllegalStateException("Missing graphics pipeline config");
        if (vertexSource == null || fragmentSource == null) throw new IllegalStateException("Missing graphics shader source");
        super.compileShaders(name, vertexSource, fragmentSource);
        return super.createGraphicsPipeline();
    }
}
