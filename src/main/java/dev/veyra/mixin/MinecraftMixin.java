package dev.veyra.mixin;

import dev.veyra.screen.ShaderPackScreen;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Unique
    private boolean veyra$f6WasDown;

    @Inject(method = "tick", at = @At("TAIL"))
    private void veyra$checkShaderMenuHotkey(CallbackInfo callback) {
        Minecraft minecraft = (Minecraft) (Object) this;
        boolean f6Down = GLFW.glfwGetKey(minecraft.getWindow().handle(), GLFW.GLFW_KEY_F6) == GLFW.GLFW_PRESS;
        if (f6Down && !veyra$f6WasDown && (minecraft.screen == null || minecraft.screen instanceof TitleScreen)) {
            minecraft.setScreen(new ShaderPackScreen());
        }
        veyra$f6WasDown = f6Down;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void veyra$restoreTerrainLayerMode(CallbackInfo callback) {
        VeyraRenderer.onClientShutdown();
    }
}
