package dev.veyra.mixin;

import dev.veyra.compat.LegacyDraws;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// VulkanMod overwrites apply(); this must be applied after that overwrite.
@Mixin(value = CompiledShaderProgram.class, priority = 1500)
public abstract class LegacyProgramBindMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void veyra$bindShaderPackInputs(CallbackInfo callback) {
        LegacyDraws.beforeBind((CompiledShaderProgram) (Object) this);
    }
}
