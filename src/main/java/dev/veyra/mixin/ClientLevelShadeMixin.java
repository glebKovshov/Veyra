package dev.veyra.mixin;

import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Like Iris, shade every face as if it pointed up while a shader pack owns lighting, unless the pack asks for
 * vanilla directional shading with oldLighting=true. VulkanMod's chunk light pipelines reach this through
 * RenderRegion.getShade, so the baked vertex colors match Iris.
 */
@Mixin(ClientLevel.class)
public class ClientLevelShadeMixin {
    @ModifyVariable(method = "getShade", at = @At("HEAD"), argsOnly = true)
    private Direction veyra$disableDirectionalShading(Direction face) {
        return VeyraRenderer.shouldDisableDirectionalShading() ? Direction.UP : face;
    }
}
