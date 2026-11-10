package dev.veyra.mixin.accessor;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** On 1.21.1 a shader state holds a supplier of the loaded core shader. */
@Mixin(RenderStateShard.ShaderStateShard.class)
public interface ShaderStateShardAccessor {
    @Accessor("shader") Optional<Supplier<ShaderInstance>> veyra$getShader();
}
