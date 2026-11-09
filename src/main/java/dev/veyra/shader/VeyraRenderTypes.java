package dev.veyra.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.veyra.mixin.accessor.RenderSetupAccessor;
import dev.veyra.mixin.accessor.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Replaces a pipeline while retaining the original render type's other state. */
final class VeyraRenderTypes {
    private VeyraRenderTypes() { }

    static RenderType replacePipeline(RenderType originalType, RenderPipeline replacement, String suffix) {
        RenderSetup originalSetup = ((RenderTypeAccessor) (Object) originalType).veyra$getSetup();
        RenderSetupAccessor setup = (RenderSetupAccessor) (Object) originalSetup;
        RenderSetup replacementSetup = RenderSetupAccessor.veyra$create(replacement,
                setup.veyra$getTextures(), setup.veyra$getUseLightmap(), setup.veyra$getUseOverlay(),
                setup.veyra$getLayeringTransform(), setup.veyra$getOutputTarget(),
                setup.veyra$getTextureTransform(), setup.veyra$getOutlineProperty(),
                setup.veyra$getAffectsCrumbling(), setup.veyra$getSortOnUpload(), setup.veyra$getBufferSize());
        return RenderTypeAccessor.veyra$create(originalType.toString() + suffix, replacementSetup);
    }
}
