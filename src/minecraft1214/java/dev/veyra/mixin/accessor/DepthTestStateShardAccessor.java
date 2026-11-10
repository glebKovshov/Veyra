package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.DepthTestStateShard.class)
public interface DepthTestStateShardAccessor {
    @Accessor("functionName") String veyra$getFunctionName();
}
