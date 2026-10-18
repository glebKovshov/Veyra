package dev.veyra.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.vulkanmod.render.engine.VkGpuDevice;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.vulkan.VK10;

import java.util.OptionalDouble;
import java.util.Random;

/** Iris-compatible deterministic noise texture, uploaded and sampled through VulkanMod. */
public final class VeyraNoiseTexture {
    public static final String SAMPLER_NAME = "VeyraNoiseSampler";
    private static VkGpuTexture texture;
    private static VkTextureView view;
    private static GpuSampler sampler;
    private static int size;

    private VeyraNoiseTexture() {
    }

    /** Prepare the deterministic source before a shader pipeline can sample it. */
    public static synchronized void ensure(int requestedSize) {
        if (requestedSize < 1 || requestedSize > 4096) {
            throw new IllegalArgumentException("Iris noise texture resolution must be between 1 and 4096.");
        }
        if (texture != null && !texture.isClosed() && size == requestedSize) {
            return;
        }
        if (!(RenderSystem.getDevice() instanceof VkGpuDevice device)) {
            throw new IllegalStateException("The Iris noise texture requires VulkanMod's Vulkan device.");
        }
        if (requestedSize > device.getMaxTextureSize()) {
            throw new IllegalArgumentException("Iris noise texture resolution exceeds VulkanMod's device limit of "
                    + device.getMaxTextureSize() + ".");
        }

        retireTexture();
        GpuSampler createdSampler = sampler == null
                ? device.createSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                        FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty())
                : sampler;
        VulkanImage image = VulkanImage.builder(requestedSize, requestedSize)
                .setName("Veyra Iris noisetex")
                .setFormat(VK10.VK_FORMAT_R8G8B8A8_UNORM)
                .setLinearFiltering(true)
                .setClamp(false)
                .createVulkanImage();
        VkGpuTexture createdTexture = device.gpuTextureFromVulkanImage(image);
        VkTextureView createdView = (VkTextureView) device.createTextureView(createdTexture);
        NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, requestedSize, requestedSize, false);
        try {
            Random random = new Random(0L);
            for (int x = 0; x < requestedSize; x++) {
                for (int y = 0; y < requestedSize; y++) {
                    pixels.setPixel(x, y, random.nextInt() | (255 << 24));
                }
            }
            device.createCommandEncoder().writeToTexture(createdTexture, pixels);
        } catch (RuntimeException | Error exception) {
            createdView.close();
            createdTexture.close();
            throw exception;
        } finally {
            pixels.close();
        }

        texture = createdTexture;
        view = createdView;
        size = requestedSize;
        sampler = createdSampler;
    }

    public static synchronized void bind(GraphicsPipeline pipeline) {
        var descriptor = pipeline.getImageDescriptor(SAMPLER_NAME);
        if (descriptor == null) {
            return;
        }
        requireReady();
        VTextureSelector.bindTexture(descriptor.imageIdx, texture.getVulkanImage());
    }

    public static synchronized void bind(VkRenderPass renderPass) {
        requireReady();
        renderPass.bindTexture(SAMPLER_NAME, view, sampler);
    }

    public static synchronized boolean hasTexture() {
        return texture != null && !texture.isClosed() && view != null && !view.isClosed();
    }

    public static synchronized GpuTextureView view() {
        requireReady();
        return view;
    }

    public static synchronized GpuSampler sampler() {
        requireReady();
        return sampler;
    }

    public static synchronized int size() {
        return size;
    }

    private static void requireReady() {
        if (!hasTexture() || sampler == null) {
            throw new IllegalStateException("The Iris noisetex sampler was used before its Vulkan texture was prepared.");
        }
    }

    public static synchronized void close() {
        closeTexture();
        sampler = null;
    }

    private static void retireTexture() {
        GpuTextureView oldView = view;
        VkGpuTexture oldTexture = texture;
        view = null;
        texture = null;
        size = 0;
        if (oldView != null || oldTexture != null) {
            MemoryManager.getInstance().addFrameOp(() -> closeResources(oldView, oldTexture));
        }
    }

    private static void closeTexture() {
        GpuTextureView oldView = view;
        VkGpuTexture oldTexture = texture;
        view = null;
        texture = null;
        size = 0;
        closeResources(oldView, oldTexture);
    }

    private static void closeResources(GpuTextureView oldView, VkGpuTexture oldTexture) {
        if (oldView != null && !oldView.isClosed()) oldView.close();
        if (oldTexture != null && !oldTexture.isClosed()) oldTexture.close();
    }
}
