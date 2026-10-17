package dev.veyra.mixin.accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("shaderManager")
    ShaderManager veyra$getShaderManager();
}
