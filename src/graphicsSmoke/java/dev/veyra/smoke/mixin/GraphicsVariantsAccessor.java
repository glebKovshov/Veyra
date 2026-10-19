package dev.veyra.smoke.mixin;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GraphicsPipeline.class,remap=false)
public interface GraphicsVariantsAccessor {
    @Accessor("graphicsPipelines") Object2LongMap<PipelineState> variants();
}
