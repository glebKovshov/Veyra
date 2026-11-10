package dev.veyra.shader;

import dev.veyra.compat.RenderPipeline;
import dev.veyra.compat.VanillaPipelines;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import dev.veyra.mixin.accessor.RenderTypeCreateInvoker;
import net.minecraft.client.renderer.RenderType;

/**
 * Minecraft 1.21.4 render types carry a shader program instead of a pipeline. The copy keeps the
 * original state and is drawn with the replacement pipeline's program.
 */
final class VeyraRenderTypes {
    private VeyraRenderTypes() { }

    static RenderType replacePipeline(RenderType originalType, RenderPipeline replacement, String suffix) {
        var state = ((RenderTypeAccessor) (Object) originalType).veyra$getSetup();
        RenderType routed = RenderTypeCreateInvoker.veyra$create(originalType.toString() + suffix,
                originalType.format(), originalType.mode(), originalType.bufferSize(),
                originalType.affectsCrumbling(), originalType.sortOnUpload(), state);
        VanillaPipelines.force(routed, replacement);
        return routed;
    }
}
