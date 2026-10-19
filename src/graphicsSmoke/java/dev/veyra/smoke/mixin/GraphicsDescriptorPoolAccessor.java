package dev.veyra.smoke.mixin;
import net.vulkanmod.vulkan.shader.DescriptorSets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=DescriptorSets.class,remap=false)
public interface GraphicsDescriptorPoolAccessor {
    @Accessor("descriptorPool") long descriptorPool();
    @Accessor("sets") long[] sets();
}
