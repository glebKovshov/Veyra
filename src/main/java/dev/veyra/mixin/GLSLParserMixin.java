package dev.veyra.mixin;

import dev.veyra.mixin.accessor.LexerAccessor;
import dev.veyra.shader.EntitySamplerBindings;
import dev.veyra.shader.ShaderPackStorageBuffers;
import dev.veyra.shader.VeyraNativeImageBindings;
import dev.veyra.shader.VeyraStorageBufferResources;
import dev.veyra.shader.VeyraStorageBufferUBO;
import net.vulkanmod.vulkan.shader.converter.GLSLParser;
import net.vulkanmod.vulkan.shader.converter.Lexer;
import net.vulkanmod.vulkan.shader.converter.Token;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.List;

@Mixin(value = GLSLParser.class, remap = false)
public abstract class GLSLParserMixin {
    @Shadow(remap = false) private List<Token> tokens;
    @Shadow(remap = false) private int currentTokenIdx;

    @Redirect(method = "parse", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/shader/converter/GLSLParser;parseAttribute()V"), remap = false)
    private void veyra$skipFunctionParameterDirections(GLSLParser parser) {
        if (!veyra$isFunctionParameterDirection()) {
            veyra$invokeParseAttribute();
        }
    }

    @Invoker(value = "parseAttribute", remap = false)
    protected abstract void veyra$invokeParseAttribute();

    private boolean veyra$isFunctionParameterDirection() {
        for (int index = currentTokenIdx - 2; index >= 0; index--) {
            Token.TokenType type = tokens.get(index).type;
            if (type == Token.TokenType.SPACING || type == Token.TokenType.COMMENT
                    || type == Token.TokenType.PREPROCESSOR) {
                continue;
            }
            return type == Token.TokenType.LEFT_PARENTHESIS || type == Token.TokenType.COMMA;
        }
        return false;
    }

    @Redirect(method = "parse", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/shader/converter/Lexer;tokenize()Ljava/util/List;"), remap = false)
    private List<Token> veyra$prepareStorageBuffers(Lexer lexer) {
        VeyraStorageBufferResources resources = VeyraStorageBufferResources.active();
        String source = ((LexerAccessor) lexer).veyra$getInput();
        if (resources == null) {
            return veyra$tokenize(new Lexer(ShaderPackStorageBuffers.stripCommentsForVulkanParser(source)));
        }

        try {
            ShaderPackStorageBuffers.PreparedSource prepared = ShaderPackStorageBuffers.prepareForVulkanParser(
                    source, resources.properties(), resources.viewWidth(), resources.viewHeight());
            if (prepared.storageBlocks().isEmpty()) {
                return veyra$tokenize(new Lexer(
                        ShaderPackStorageBuffers.stripCommentsForVulkanParser(prepared.source())));
            }
            for (ShaderPackStorageBuffers.StorageBlock block : prepared.storageBlocks().values()) {
                resources.registerBlock(block);
                resources.storageBuffer(block.bufferObjectBinding());
            }
            return veyra$tokenize(new Lexer(
                    ShaderPackStorageBuffers.stripCommentsForVulkanParser(prepared.source())));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare shader-pack storage buffers for VulkanMod's GLSL parser: "
                    + exception.getMessage(), exception);
        }
    }

    private static List<Token> veyra$tokenize(Lexer lexer) {
        try {
            return lexer.tokenize();
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message == null || !message.contains("Unrecognized char: \\")) throw exception;
            LexerAccessor accessor = (LexerAccessor) lexer;
            String input = accessor.veyra$getInput();
            int offset = Math.max(0, Math.min(accessor.veyra$getCurrentPosition(), input.length()));
            int lineStart = input.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
            int lineEnd = input.indexOf('\n', offset);
            if (lineEnd < 0) lineEnd = input.length();
            String line = input.substring(lineStart, lineEnd).replace("\r", "");
            if (line.length() > 200) line = line.substring(0, 200) + "...";
            int lineNumber = 1;
            for (int index = 0; index < lineStart; index++) {
                if (input.charAt(index) == '\n') lineNumber++;
            }
            throw new IllegalStateException(message + " at GLSL line " + lineNumber
                    + ", column " + (offset - lineStart + 1) + ": " + line.replace("\\", "\\\\"), exception);
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

    @Inject(method = "getSamplerList", at = @At("RETURN"), cancellable = true, remap = false)
    private void veyra$replaceNativeStorageImageDescriptors(
            CallbackInfoReturnable<List<ImageDescriptor>> callback) {
        VeyraNativeImageBindings bindings = VeyraNativeImageBindings.active();
        List<ImageDescriptor> descriptors = callback.getReturnValue();
        if (bindings != null) {
            try {
                descriptors = bindings.replaceSamplerDescriptors(descriptors);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not bind shader-pack graphics storage images: "
                        + exception.getMessage(), exception);
            }
        }
        callback.setReturnValue(EntitySamplerBindings.reserveNormalSamplerSlot(descriptors));
    }

    @Inject(method = "getOutput", at = @At("RETURN"), cancellable = true, remap = false)
    private void veyra$restoreNativeStorageImageDeclarations(CallbackInfoReturnable<String> callback) {
        VeyraNativeImageBindings bindings = VeyraNativeImageBindings.active();
        if (bindings != null) {
            try {
                callback.setReturnValue(bindings.restoreOutput(callback.getReturnValue()));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not restore shader-pack graphics storage-image declarations: "
                        + exception.getMessage(), exception);
            }
        }
    }
}
