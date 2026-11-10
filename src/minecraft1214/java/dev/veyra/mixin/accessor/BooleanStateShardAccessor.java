package dev.veyra.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.RenderStateShard$BooleanStateShard")
public interface BooleanStateShardAccessor {
    @Accessor("enabled") boolean veyra$isEnabled();
}
