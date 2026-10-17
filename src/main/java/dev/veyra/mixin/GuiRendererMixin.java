package dev.veyra.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
    private void veyra$applyFinalPass(GpuBufferSlice fogBuffer, CallbackInfo callback) {
        VeyraRenderer.render(Minecraft.getInstance());
    }
}
