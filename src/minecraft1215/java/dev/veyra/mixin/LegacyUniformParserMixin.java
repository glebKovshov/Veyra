package dev.veyra.mixin;

import net.vulkanmod.vulkan.shader.converter.UniformParser;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = UniformParser.class, remap = false)
public abstract class LegacyUniformParserMixin {
    @Redirect(method = "createUBO", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/shader/layout/Uniform;createUniformInfo(Ljava/lang/String;Ljava/lang/String;)Lnet/vulkanmod/vulkan/shader/layout/Uniform$Info;"))
    private Uniform.Info veyra$integerVectorLayout(String type, String name) {
        return dev.veyra.vulkan.VeyraUniformBlocks.field(type, name);
    }
}
