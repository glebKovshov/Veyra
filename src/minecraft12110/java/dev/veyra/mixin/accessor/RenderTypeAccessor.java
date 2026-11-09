package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeRenderType.class)
public interface RenderTypeAccessor {
    @Accessor("state") RenderType.CompositeState veyra$getSetup();
}
