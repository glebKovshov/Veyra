package dev.veyra.mixin;

import dev.veyra.mixin.accessor.LexerAccessor;
import dev.veyra.shader.ShaderPackStorageBuffers;
import dev.veyra.shader.VeyraStorageBufferResources;
import dev.veyra.shader.VeyraStorageBufferUBO;
import net.vulkanmod.vulkan.shader.converter.GLSLParser;
import net.vulkanmod.vulkan.shader.converter.Lexer;
import net.vulkanmod.vulkan.shader.converter.Token;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.List;

@Mixin(value = GLSLParser.class, remap = false)
public abstract class GLSLParserMixin {
    @Redirect(method = "parse", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/shader/converter/Lexer;tokenize()Ljava/util/List;"), remap = false)
    private List<Token> veyra$prepareStorageBuffers(Lexer lexer) {
        VeyraStorageBufferResources resources = VeyraStorageBufferResources.active();
        String source = ((LexerAccessor) lexer).veyra$getInput();
        if (resources == null) return lexer.tokenize();

        try {
            ShaderPackStorageBuffers.PreparedSource prepared = ShaderPackStorageBuffers.prepareForVulkanParser(
                    source, resources.properties(), resources.viewWidth(), resources.viewHeight());
            if (prepared.storageBlocks().isEmpty()) return lexer.tokenize();
            for (ShaderPackStorageBuffers.StorageBlock block : prepared.storageBlocks().values()) {
                resources.registerBlock(block);
                resources.storageBuffer(block.bufferObjectBinding());
            }
            return new Lexer(prepared.source()).tokenize();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare shader-pack storage buffers for VulkanMod's GLSL parser: "
                    + exception.getMessage(), exception);
        }
    }

    @Inject(method = "createUBOs", at = @At("RETURN"), cancellable = true, remap = false)
    private void veyra$replaceStorageBufferDescriptors(CallbackInfoReturnable<UBO[]> callback) {
        VeyraStorageBufferResources resources = VeyraStorageBufferResources.active();
        if (resources == null) return;
        UBO[] descriptors = callback.getReturnValue();
        for (int index = 0; index < descriptors.length; index++) {
            UBO descriptor = descriptors[index];
            ShaderPackStorageBuffers.StorageBlock block = resources.block(descriptor.name);
            if (block == null) continue;
            try {
                VeyraStorageBufferResources.StorageBuffer storageBuffer =
                        resources.storageBuffer(block.bufferObjectBinding());
                descriptors[index] = new VeyraStorageBufferUBO(descriptor, storageBuffer);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not bind active shader-pack storage buffer '"
                        + block.blockName() + "': " + exception.getMessage(), exception);
            }
        }
        callback.setReturnValue(descriptors);
    }
}
