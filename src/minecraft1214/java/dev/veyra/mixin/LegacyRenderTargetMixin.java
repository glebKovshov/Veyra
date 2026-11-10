package dev.veyra.mixin;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.veyra.accessor.RenderTargetAccess;
import dev.veyra.compat.GpuTexture;
import dev.veyra.compat.VkGpuTexture;
import dev.veyra.vulkan.LegacyMainPassAccess;
import dev.veyra.vulkan.LegacyTextureView;
import dev.veyra.vulkan.LegacyVkTextureView;
import net.vulkanmod.gl.VkGlFramebuffer;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.4 render targets hold texture ids. The ids are read from the fields: VulkanMod's
 * id getter also flushes a pending clear, which must not happen when Veyra only inspects a target.
 */
@Mixin(RenderTarget.class)
public abstract class LegacyRenderTargetMixin implements RenderTargetAccess {
    @Unique private static final int GL_FRAMEBUFFER = 0x8D40;
    @Unique private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    @Unique private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    @Unique private static final int GL_TEXTURE_2D = 0x0DE1;
    @Shadow protected int colorTextureId;
    @Shadow protected int depthBufferId;
    @Shadow public int frameBufferId;
    @Shadow @Final public boolean useDepth;
    @Shadow public abstract int getColorTextureId();
    @Unique private LegacyTextureView veyra$colorView;
    @Unique private LegacyTextureView veyra$depthView;

    public GpuTexture veyra$getColorTexture() {
        // VulkanMod's main target reports the current swapchain image.
        return VkGpuTexture.of((Object) this instanceof MainTarget ? getColorTextureId() : colorTextureId);
    }

    public void veyra$setColorTexture(GpuTexture texture) {
        colorTextureId = ((VkGpuTexture) texture).glId();
        veyra$attach(GL_COLOR_ATTACHMENT0, colorTextureId);
    }

    public GpuTexture veyra$getDepthTexture() {
        if ((Object) this instanceof MainTarget) {
            return ((LegacyMainPassAccess) Renderer.getInstance().getMainPass()).getDepthAttachment();
        }
        return useDepth ? VkGpuTexture.of(depthBufferId) : null;
    }

    public void veyra$setDepthTexture(GpuTexture texture) {
        depthBufferId = ((VkGpuTexture) texture).glId();
        veyra$attach(GL_DEPTH_ATTACHMENT, depthBufferId);
    }

    // Attaching rebuilds the native framebuffer and begins rendering to it; restore the previous binding.
    @Unique private void veyra$attach(int attachment, int texture) {
        if (frameBufferId <= 0 || VkGlFramebuffer.getFramebuffer(frameBufferId) == null) return;
        var previous = dev.veyra.compat.LegacyDraws.binding();
        VkGlFramebuffer.bindFramebuffer(GL_FRAMEBUFFER, frameBufferId);
        VkGlFramebuffer.framebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture, 0);
        dev.veyra.compat.LegacyDraws.restore(previous);
    }

    // Creating or resizing the attachments binds and begins rendering to the new framebuffer, then
    // binds the default framebuffer. OpenGL allows both, but they would leave Vulkan inside a render
    // pass the caller did not begin.
    @Unique private dev.veyra.compat.LegacyDraws.Binding veyra$creationBinding;
    @Unique private int veyra$creationDepth;

    @Inject(method = {"createBuffers", "resize"}, at = @At("HEAD"))
    private void veyra$rememberBinding(int width, int height, CallbackInfo callback) {
        if (veyra$creationDepth++ == 0 && net.vulkanmod.vulkan.Renderer.isRecording()) {
            veyra$creationBinding = dev.veyra.compat.LegacyDraws.binding();
        }
    }

    @Inject(method = {"createBuffers", "resize"}, at = @At("RETURN"))
    private void veyra$restoreBinding(int width, int height, CallbackInfo callback) {
        if (--veyra$creationDepth > 0) return;
        if (veyra$creationBinding != null) dev.veyra.compat.LegacyDraws.restore(veyra$creationBinding);
        veyra$creationBinding = null;
    }

    public LegacyTextureView veyra$getColorTextureView() {
        return veyra$colorView = veyra$view(veyra$getColorTexture(), veyra$colorView);
    }
    public LegacyTextureView veyra$getDepthTextureView() {
        return veyra$depthView = veyra$view(veyra$getDepthTexture(), veyra$depthView);
    }
    public void veyra$setColorTextureView(LegacyTextureView view) {
        veyra$validate(veyra$getColorTexture(), view);
        if (veyra$colorView != null && veyra$colorView != view) veyra$colorView.close();
        veyra$colorView = view;
    }
    public void veyra$setDepthTextureView(LegacyTextureView view) {
        veyra$validate(veyra$getDepthTexture(), view);
        if (veyra$depthView != null && veyra$depthView != view) veyra$depthView.close();
        veyra$depthView = view;
    }

    @Unique private static LegacyTextureView veyra$view(GpuTexture texture, LegacyTextureView previous) {
        if (previous != null && previous.texture() == texture && !previous.isClosed()) return previous;
        if (previous != null) previous.close();
        return texture == null || texture.isClosed() ? null : LegacyVkTextureView.create(texture);
    }
    @Unique private static void veyra$validate(GpuTexture texture, LegacyTextureView view) {
        if (view != null && view.textureForBinding() != texture) {
            throw new IllegalArgumentException("Texture view must refer to the target attachment.");
        }
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"))
    private void veyra$closeBorrowedViews(CallbackInfo callback) {
        if (veyra$colorView != null) veyra$colorView.close();
        if (veyra$depthView != null) veyra$depthView.close();
        veyra$colorView = null;
        veyra$depthView = null;
    }
}
