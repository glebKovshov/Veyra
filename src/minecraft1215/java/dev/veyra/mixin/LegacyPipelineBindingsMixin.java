package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.veyra.accessor.LegacyConvertedShader;
import dev.veyra.vulkan.LegacyUniformMetadata;
import net.vulkanmod.interfaces.shader.ExtendedRenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.converter.GlslConverter;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = VkGpuDevice.class, remap = false)
public abstract class LegacyPipelineBindingsMixin {
    @WrapOperation(method = "compilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Ljava/util/function/BiFunction;)V",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/shader/converter/GlslConverter;process(Lcom/mojang/blaze3d/vertex/VertexFormat;Ljava/lang/String;Ljava/lang/String;)V"))
    private void veyra$applyPipelineDefines(GlslConverter converter, com.mojang.blaze3d.vertex.VertexFormat format,
            String vertex, String fragment, Operation<Void> original,
            @Local(argsOnly = true) com.mojang.blaze3d.pipeline.RenderPipeline pipeline) {
        if (!pipeline.getFragmentShader().getNamespace().equals("veyra")) {
            original.call(converter, format, vertex, fragment);
            return;
        }
        try {
            original.call(converter, format,
                    dev.veyra.shader.LegacyGlslSource.preprocess(pipeline.getVertexShader() + ".vsh", vertex, pipeline.getShaderDefines()),
                    dev.veyra.shader.LegacyGlslSource.preprocess(pipeline.getFragmentShader() + ".fsh", fragment, pipeline.getShaderDefines()));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not preprocess " + pipeline.getLocation(), failure);
        }
    }

    @WrapOperation(method = "compilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Ljava/util/function/BiFunction;)V",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/render/shader/ShaderLoadUtil;getJsonConfig(Ljava/lang/String;Ljava/lang/String;)Lcom/google/gson/JsonObject;"))
    private com.google.gson.JsonObject veyra$useProvidedPackSources(String path, String name,
            Operation<com.google.gson.JsonObject> original,
            @Local(argsOnly = true) com.mojang.blaze3d.pipeline.RenderPipeline pipeline) {
        // A vanilla vertex ID must not select a native JSON shader over Veyra's supplied fragment.
        return pipeline.getFragmentShader().getNamespace().equals("veyra") ? null : original.call(path, name);
    }

    @WrapOperation(method = "compilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Ljava/util/function/BiFunction;)V",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/shader/Pipeline$Builder;setUniforms(Ljava/util/List;Ljava/util/List;)V"))
    private void veyra$includeStorageBuffers(Pipeline.Builder builder, List<UBO> buffers,
            List<ImageDescriptor> images, Operation<Void> original, @Local GlslConverter converter) {
        var merged = new ArrayList<>(buffers);
        merged.addAll(((LegacyConvertedShader) converter).veyra$storageDescriptors());
        original.call(builder, merged, images);
    }

    @WrapOperation(method = "compilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Ljava/util/function/BiFunction;)V",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/interfaces/shader/ExtendedRenderPipeline;setupUniformSuppliers(Lnet/vulkanmod/vulkan/shader/descriptor/UBO;)V"))
    private void veyra$installNamedUniforms(ExtendedRenderPipeline pipeline, UBO block,
            Operation<Void> original, @Local GlslConverter converter) {
        LegacyUniformMetadata.install(pipeline.getProgram(), block, ((LegacyConvertedShader) converter).veyra$uniformTypes());
        original.call(pipeline, block);
    }
}
