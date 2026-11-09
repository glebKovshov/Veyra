package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.veyra.accessor.LegacyConvertedShader;
import dev.veyra.shader.EntitySamplerBindings;
import dev.veyra.shader.LegacyGlslSource;
import dev.veyra.shader.VeyraNativeImageBindings;
import dev.veyra.shader.VeyraStorageBufferResources;
import java.io.IOException;
import java.util.List;
import net.vulkanmod.vulkan.shader.converter.GlslConverter;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlslConverter.class, remap = false)
public abstract class GLSLParserMixin implements LegacyConvertedShader {
    @Shadow private String fshConverted;
    @Unique private List<UBO> veyra$storageDescriptors = List.of();
    @Unique private java.util.Map<String, String> veyra$uniformTypes = java.util.Map.of();

    @WrapMethod(method = "process")
    private void veyra$prepareLegacyShader(VertexFormat format, String vertex, String fragment, Operation<Void> original) {
        veyra$storageDescriptors = List.of();
        veyra$uniformTypes = java.util.Map.of();
        try {
            var storage = VeyraStorageBufferResources.active();
            if (storage != null) {
                var prepared = storage.prepareDirectCompilation(vertex, fragment, null);
                vertex = prepared.vertexSource();
                fragment = prepared.fragmentSource();
                veyra$storageDescriptors = prepared.storageDescriptors();
            }
            vertex = LegacyGlslSource.prepare(vertex);
            fragment = LegacyGlslSource.stripUnusedFragmentInputs(LegacyGlslSource.prepare(fragment));
            veyra$uniformTypes = LegacyGlslSource.uniformTypes(vertex, fragment);
            var outputs = LegacyGlslSource.fragment(fragment);
            original.call(format, vertex, outputs.source());
            fshConverted = outputs.restore(fshConverted);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not prepare legacy graphics shaders: " + failure.getMessage(), failure);
        }
    }

    @Override public List<UBO> veyra$storageDescriptors() { return veyra$storageDescriptors; }
    @Override public java.util.Map<String, String> veyra$uniformTypes() { return veyra$uniformTypes; }

    @Inject(method = "getSamplerList", at = @At("RETURN"), cancellable = true)
    private void veyra$bindImages(CallbackInfoReturnable<List<ImageDescriptor>> callback) {
        var descriptors = callback.getReturnValue();
        var bindings = VeyraNativeImageBindings.active();
        try {
            if (bindings != null) descriptors = bindings.replaceSamplerDescriptors(descriptors);
            callback.setReturnValue(EntitySamplerBindings.reserveNormalSamplerSlot(descriptors));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not bind legacy graphics images", failure);
        }
    }

    @Inject(method = {"getVshConverted", "getFshConverted"}, at = @At("RETURN"), cancellable = true)
    private void veyra$restoreImages(CallbackInfoReturnable<String> callback) {
        var bindings = VeyraNativeImageBindings.active();
        if (bindings == null) return;
        try { callback.setReturnValue(bindings.restoreOutput(callback.getReturnValue())); }
        catch (IOException failure) { throw new IllegalStateException("Could not restore legacy graphics images", failure); }
    }
}
