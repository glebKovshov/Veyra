package dev.veyra.smoke.mixin;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=MemoryManager.class,remap=false)
public interface GraphicsMemoryAccessor {
    @Accessor("buffers") static Long2ReferenceOpenHashMap<Buffer> buffers(){throw new AssertionError();}
    @Accessor("images") static Long2ReferenceOpenHashMap<VulkanImage> images(){throw new AssertionError();}
    @Accessor("frameOps") ObjectArrayList<?>[] frameOps();
    @Accessor("freeableBuffers") ObjectArrayList<?>[] freeableBuffers();
    @Accessor("freeableImages") ObjectArrayList<?>[] freeableImages();
}
