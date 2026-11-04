package dev.veyra.mixin.accessor;

import net.vulkanmod.render.chunk.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WorldRenderer.class, remap = false)
public interface VulkanWorldRendererAccessor {
    /** Forces the next setupRenderer call to rebuild the section graph for its frustum. */
    @Accessor(value = "graphNeedsUpdate", remap = false)
    void veyra$setGraphNeedsUpdate(boolean graphNeedsUpdate);
}
