package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.WriteMaskStateShard.class)
public interface WriteMaskStateShardAccessor {
    @Accessor("writeColor") boolean veyra$writesColor();
    @Accessor("writeDepth") boolean veyra$writesDepth();
}
