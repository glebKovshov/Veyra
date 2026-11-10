package dev.veyra.mixin.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("shaderState") RenderStateShard.ShaderStateShard veyra$getShaderState();
    @Accessor("transparencyState") RenderStateShard.TransparencyStateShard veyra$getTransparencyState();
    @Accessor("depthTestState") RenderStateShard.DepthTestStateShard veyra$getDepthTestState();
    @Accessor("cullState") RenderStateShard.CullStateShard veyra$getCullState();
    @Accessor("writeMaskState") RenderStateShard.WriteMaskStateShard veyra$getWriteMaskState();
}
