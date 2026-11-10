package dev.veyra.mixin.accessor;

import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.ShaderStateShard.class)
public interface ShaderStateShardAccessor {
    @Accessor("shader") Optional<ShaderProgram> veyra$getShader();
}
