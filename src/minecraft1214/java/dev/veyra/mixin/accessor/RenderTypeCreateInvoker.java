package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeCreateInvoker {
    @Invoker("create")
    static RenderType.CompositeRenderType veyra$create(String name, VertexFormat format, VertexFormat.Mode mode,
            int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, RenderType.CompositeState state) {
        throw new AssertionError();
    }
}
