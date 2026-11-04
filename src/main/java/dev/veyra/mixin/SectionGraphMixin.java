package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import net.vulkanmod.render.chunk.util.ResettableQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SectionGraph.class, remap = false)
public abstract class SectionGraphMixin {
    @Shadow @Final private ResettableQueue<RenderSection> blockEntitiesSections;

    /**
     * The shadow pass selects sections without camera occlusion culling, which VulkanMod implements
     * in its spectator traversal. That traversal skips block-entity collection, so collect them for
     * every in-frustum shadow section; otherwise chests, signs and similar casters lose their shadows.
     */
    @Redirect(method = "updateRenderChunksSpectator", at = @At(value = "INVOKE",
            target = "Lnet/vulkanmod/render/chunk/RenderSection;isDirty()Z"), remap = false)
    private boolean veyra$collectShadowBlockEntities(RenderSection section) {
        if (VeyraShadowRenderer.isSelectingShadowSections() && section.containsBlockEntities()) {
            blockEntitiesSections.ensureCapacity(1);
            blockEntitiesSections.add(section);
        }
        return section.isDirty();
    }
}
