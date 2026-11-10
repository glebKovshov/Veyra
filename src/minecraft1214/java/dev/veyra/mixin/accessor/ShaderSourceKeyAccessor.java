package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.shaders.CompiledShader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$ShaderSourceKey")
public interface ShaderSourceKeyAccessor {
    @Accessor("id") ResourceLocation veyra$getId();
    @Accessor("type") CompiledShader.Type veyra$getType();
}
