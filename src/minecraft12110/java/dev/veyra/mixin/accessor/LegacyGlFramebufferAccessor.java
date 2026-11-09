package dev.veyra.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.vulkanmod.gl.VkGlFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = VkGlFramebuffer.class, remap = false)
public interface LegacyGlFramebufferAccessor {
    @Accessor("map")
    static Int2ReferenceOpenHashMap<VkGlFramebuffer> veyra$getFramebuffers() { throw new AssertionError(); }
    @Invoker("cleanUp") void veyra$cleanUp(boolean attachments);
}
