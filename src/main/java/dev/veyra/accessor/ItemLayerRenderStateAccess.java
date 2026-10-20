package dev.veyra.accessor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Bridge implemented by the invoker mixin for a single item render layer. */
public interface ItemLayerRenderStateAccess {
    void veyra$submit(PoseStack poseStack, SubmitNodeCollector collector,
                      int packedLight, int packedOverlay, int outlineColor);
}
