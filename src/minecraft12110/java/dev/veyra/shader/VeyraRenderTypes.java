package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.RenderType;

/** The old composite state can be reused intact, including texture and line state. */
final class VeyraRenderTypes {
    private VeyraRenderTypes() { }

    static RenderType replacePipeline(RenderType originalType, RenderPipeline replacement, String suffix) {
        var state = ((RenderTypeAccessor) (Object) originalType).veyra$getSetup();
        return RenderType.create(originalType.toString() + suffix, originalType.bufferSize(),
                originalType.affectsCrumbling(), originalType.sortOnUpload(), replacement, state);
    }
}
