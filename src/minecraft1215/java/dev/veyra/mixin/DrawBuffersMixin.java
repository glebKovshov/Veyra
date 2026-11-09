package dev.veyra.mixin;

import dev.veyra.shader.VeyraShadowRenderer;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DrawBuffers.class, remap = false)
public abstract class DrawBuffersMixin {
    private static final int VEYRA$ALL_FACINGS = (1 << QuadFacing.COUNT) - 1;

    @Shadow
    private int getMask(Vec3 camera, RenderSection section) {
        throw new AssertionError();
    }

    /**
     * VulkanMod's back-face culling keeps only quad facings visible from the player camera. In the
     * shadow pass that removes faces seen by the light, so shadows change shape and shift as the
     * player moves. Iris disables Sodium's equivalent block-face culling for shadows; draw every
     * facing bucket here. Sections compiled with culling store quads per facing, so the unculled
     * branch (which reads only the undefined bucket) cannot be used instead.
     */
    @Redirect(method = {"buildDrawBatchesIndirect", "buildDrawBatchesDirect"}, at = @At(value = "INVOKE",
            target = "Lnet/vulkanmod/render/chunk/buffer/DrawBuffers;getMask(Lnet/minecraft/world/phys/Vec3;Lnet/vulkanmod/render/chunk/RenderSection;)I"),
            remap = false)
    private int veyra$drawAllFacingsForShadows(DrawBuffers buffers, Vec3 camera, RenderSection section) {
        return VeyraShadowRenderer.isRendering() ? VEYRA$ALL_FACINGS : getMask(camera, section);
    }
}
