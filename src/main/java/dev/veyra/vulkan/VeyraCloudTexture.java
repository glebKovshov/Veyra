package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.util.OptionalDouble;

/** Vulkan upload of the same resource image VulkanMod uses to construct its cloud grid. */
public final class VeyraCloudTexture {
    public static final String SAMPLER_NAME = "VeyraCloudTextureSampler";
    private static final Identifier CLOUD_TEXTURE = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    private static final int MAX_TEXTURE_SIZE = 4096;

    private static VkGpuTexture texture;
    private static VkTextureView view;
    private static GpuSampler sampler;
    private static int width;

    private VeyraCloudTexture() {
    }

    public static synchronized int ensure() throws IOException {
        if (hasTexture()) return width;
        var resource = Minecraft.getInstance().getResourceManager().getResource(CLOUD_TEXTURE)
                .orElseThrow(() -> new IOException("The active resource packs do not provide " + CLOUD_TEXTURE + "."));
        try (var stream = resource.open(); NativeImage pixels = NativeImage.read(stream)) {
            if (pixels.getWidth() != pixels.getHeight()) {
                throw new IOException("The Minecraft cloud texture must be square for VulkanMod's cloud grid.");
            }
            if (pixels.getWidth() < 1 || pixels.getWidth() > MAX_TEXTURE_SIZE) {
                throw new IOException("The Minecraft cloud texture exceeds Veyra's 4096-pixel size limit.");
            }
            install(upload(pixels));
        }
        return width;
    }

    /** Re-upload after Minecraft reloads the resource pack containing the cloud image. */
    public static synchronized void reloadIfLoaded() {
        if (!hasTexture()) return;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(CLOUD_TEXTURE)
                    .orElseThrow(() -> new IOException("The active resource packs do not provide " + CLOUD_TEXTURE + "."));
            try (var stream = resource.open(); NativeImage pixels = NativeImage.read(stream)) {
                if (pixels.getWidth() != pixels.getHeight() || pixels.getWidth() < 1
                        || pixels.getWidth() > MAX_TEXTURE_SIZE) {
                    throw new IOException("The reloaded Minecraft cloud texture has unsupported dimensions.");
                }
                install(upload(pixels));
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not reload Veyra's cloud texture.", exception);
        }
    }

    public static synchronized void bind(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(SAMPLER_NAME);
        if (descriptor == null) return;
        requireReady();
        descriptor.setLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        descriptor.isReadOnlyLayout = false;
        VRenderSystemBridge.bind(descriptor.imageIdx, view, texture.getVulkanImage());
    }

    public static synchronized void bind(VkRenderPass renderPass) {
        requireReady();
        renderPass.bindTexture(SAMPLER_NAME, view, sampler);
    }

    public static synchronized boolean hasTexture() {
        return texture != null && !texture.isClosed() && view != null && !view.isClosed();
    }

    public static synchronized int width() {
        return width;
    }

    public static synchronized void close() {
        GpuTextureView oldView = view;
        VkGpuTexture oldTexture = texture;
        GpuSampler oldSampler = sampler;
        view = null;
        texture = null;
        width = 0;
        sampler = null;
        closeResources(oldView, oldTexture);
        if (oldSampler != null) oldSampler.close();
    }

    private static UploadedTexture upload(NativeImage pixels) throws IOException {
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IOException("The Minecraft cloud texture requires VulkanMod's Vulkan device.");
        }
        if (pixels.getWidth() > device.getMaxTextureSize()) {
            throw new IOException("The Minecraft cloud texture exceeds VulkanMod's device limit of "
                    + device.getMaxTextureSize() + ".");
        }

        boolean createdSamplerHere = sampler == null;
        GpuSampler createdSampler = createdSamplerHere
                ? device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                        FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty())
                : sampler;
        VulkanImage image = null;
        VkGpuTexture createdTexture = null;
        VkTextureView createdView = null;
        try {
            image = VulkanImage.builder(pixels.getWidth(), pixels.getHeight())
                    .setName("Veyra cloud texture")
                    .setFormat(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                    .setLinearFiltering(false)
                    .setClamp(false)
                    .createVulkanImage();
            createdTexture = device.gpuTextureFromVulkanImage(image);
            image = null; // VkGpuTexture now owns the Vulkan image.
            createdView = (VkTextureView) device.createTextureView(createdTexture);
            device.createCommandEncoder().writeToTexture(createdTexture, pixels);
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(createdView, failure);
            closeAfterFailure(createdTexture, failure);
            if (image != null) {
                try {
                    image.free();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (createdSamplerHere) closeAfterFailure(createdSampler, failure);
            throw failure;
        }

        return new UploadedTexture(createdTexture, createdView, createdSampler, pixels.getWidth());
    }

    private static void install(UploadedTexture uploaded) {
        VkTextureView oldView = view;
        VkGpuTexture oldTexture = texture;
        texture = uploaded.texture();
        view = uploaded.view();
        width = uploaded.width();
        sampler = uploaded.sampler();
        retire(oldView, oldTexture);
    }

    private static void requireReady() {
        if (!hasTexture() || sampler == null) {
            throw new IllegalStateException("The cloud texture sampler was used before its Vulkan texture was prepared.");
        }
    }

    private static void retire(GpuTextureView oldView, VkGpuTexture oldTexture) {
        if (oldView == null && oldTexture == null) return;
        MemoryManager.getInstance().addFrameOp(() -> {
            closeResources(oldView, oldTexture);
        });
    }

    private static void closeResources(GpuTextureView oldView, VkGpuTexture oldTexture) {
        if (oldView != null && !oldView.isClosed()) oldView.close();
        if (oldTexture != null && !oldTexture.isClosed()) oldTexture.close();
    }

    private static void closeAfterFailure(AutoCloseable resource, Throwable failure) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private record UploadedTexture(VkGpuTexture texture, VkTextureView view, GpuSampler sampler, int width) { }

    private static final class VRenderSystemBridge {
        private static void bind(int index, VkTextureView textureView, VulkanImage image) {
            net.vulkanmod.vulkan.VRenderSystem.setShaderTexture(index, textureView);
            VTextureSelector.bindTexture(index, image);
        }
    }
}
