package dev.veyra.smoke.mixin;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.DescriptorSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Pipeline.class,remap=false)
public interface GraphicsNativePipelineAccessor {
    @Accessor("descriptorSetLayout") long descriptorSetLayout();
    @Accessor("pipelineLayout") long pipelineLayout();
    @Accessor("descriptorSets") DescriptorSets[] descriptorSets();
}
