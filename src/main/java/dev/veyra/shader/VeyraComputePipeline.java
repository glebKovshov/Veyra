package dev.veyra.shader;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.buffer.UniformBuffer;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.AlignedStruct;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/** Vulkan compute pipeline that uses VulkanMod's global frame UBO and descriptor lifecycle. */
public final class VeyraComputePipeline extends Pipeline {
    private final int localSizeX;
    private final int localSizeY;
    private final int localSizeZ;
    private long computePipeline;
    private boolean registered;

    private VeyraComputePipeline(String name, ComputeShaderAdapter.AdaptedStage shader,
                                 List<? extends ImageDescriptor> descriptors) throws IOException {
        super(name);
        requireGraphicsQueueComputeSupport();
        validateLocalSize(name, shader.localSizeX(), shader.localSizeY(), shader.localSizeZ());
        this.localSizeX = shader.localSizeX();
        this.localSizeY = shader.localSizeY();
        this.localSizeZ = shader.localSizeZ();

        AlignedStruct.Builder uniformBuilder = AlignedStruct.builder();
        for (ShaderPackUniformAdapter.UniformField field : shader.uniforms()) {
            uniformBuilder.addUniform(field.type(), field.name());
        }
        UBO uniforms = uniformBuilder.buildUBO("VeyraComputeUniforms", 0, VK_SHADER_STAGE_COMPUTE_BIT);
        uniforms.setUseGlobalBuffer(true);
        this.buffers = List.of(uniforms);
        this.imageDescriptors = new ArrayList<>(descriptors);
        this.pushConstants = null;

        try {
            createDescriptorSetLayout();
            createPipelineLayout();
            createDescriptorSets(Renderer.getFramesNum());
            try (SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShader(
                    name, shader.source(), SPIRVUtils.ShaderKind.COMPUTE_SHADER)) {
                long module = createShaderModule(spirv.bytecode());
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                            .sType$Default()
                            .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                            .module(module)
                            .pName(stack.UTF8("main"));
                    VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                    pipelineInfo.get(0)
                            .sType$Default()
                            .stage(stage)
                            .layout(pipelineLayout)
                            .basePipelineHandle(VK_NULL_HANDLE)
                            .basePipelineIndex(-1);
                    LongBuffer handle = stack.mallocLong(1);
                    int result = vkCreateComputePipelines(Vulkan.getVkDevice(), PIPELINE_CACHE,
                            pipelineInfo, null, handle);
                    if (result != VK_SUCCESS) {
                        throw new IOException("Could not create compute pipeline " + name
                                + " (VkResult " + result + ").");
                    }
                    computePipeline = handle.get(0);
                } finally {
                    vkDestroyShaderModule(Vulkan.getVkDevice(), module, null);
                }
            }
        } catch (RuntimeException | IOException exception) {
            cleanUpNativeState();
            throw exception;
        }
        PIPELINES.add(this);
        registered = true;
    }

    public static VeyraComputePipeline create(String name, ComputeShaderAdapter.AdaptedStage shader,
                                              List<? extends ImageDescriptor> descriptors) throws IOException {
        if (shader.uniforms().isEmpty()) {
            throw new IOException("Compute program " + name + " has no supported uniforms.");
        }
        for (ImageDescriptor descriptor : descriptors) {
            descriptor.setLayout(VK_IMAGE_LAYOUT_GENERAL);
            // Shader-pack images remain in GENERAL so the same image can be read and written
            // through separate sampled/storage descriptors without VulkanMod applying stale layouts.
            descriptor.isReadOnlyLayout = false;
            if (descriptor.useSampler) {
                VulkanImage image = descriptor.getImage();
                if (image == null) throw new IOException("Compute sampler " + descriptor.name + " has no image.");
                boolean integerSampler = descriptor.qualifier.startsWith("usampler")
                        || descriptor.qualifier.startsWith("isampler");
                boolean linear = !integerSampler && (!(image instanceof dev.veyra.vulkan.VeyraImage3D volume)
                        || volume.supportsLinearFiltering());
                image.setSampler(SamplerManager.getSampler(true, linear, 0));
            }
        }
        return new VeyraComputePipeline(name, shader, descriptors);
    }

    public void dispatchWorkGroups(int x, int y, int z) {
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new IllegalArgumentException("Compute work-group counts must be positive.");
        }
        var limits = DeviceManager.deviceProperties.limits();
        if (x > limits.maxComputeWorkGroupCount(0)
                || y > limits.maxComputeWorkGroupCount(1)
                || z > limits.maxComputeWorkGroupCount(2)) {
            throw new IllegalArgumentException("Compute work-group counts for " + name + " exceed device limits.");
        }

        Renderer renderer = Renderer.getInstance();
        if (!Renderer.isRecording() || Renderer.getCommandBuffer() == null) {
            throw new IllegalStateException("Compute program " + name + " can only dispatch while VulkanMod records a frame.");
        }
        if (renderer.getBoundRenderPass() != null) {
            throw new IllegalStateException("Compute program " + name + " cannot dispatch inside a render pass.");
        }
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        int frame = Renderer.getCurrentFrame();
        renderer.addUsedPipeline(this);
        UniformBuffer globalUniformBuffer = Renderer.getDrawer().getUniformBuffer();
        descriptorSets[frame].bindSets(commandBuffer, globalUniformBuffer, VK_PIPELINE_BIND_POINT_COMPUTE);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
        vkCmdDispatch(commandBuffer, x, y, z);
    }

    /** Inserts a GENERAL-layout memory dependency between shader/transfer producers and consumers. */
    public static void memoryBarrier(VkCommandBuffer commandBuffer, int sourceStages, int destinationStages,
                                     int sourceAccess, int destinationAccess) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier.Buffer barrier = VkMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(sourceAccess)
                    .dstAccessMask(destinationAccess);
            vkCmdPipelineBarrier(commandBuffer, sourceStages, destinationStages, 0,
                    barrier, null, null);
        }
    }

    @Override
    public void cleanUp() {
        if (registered) {
            PIPELINES.remove(this);
            Renderer.getInstance().removeUsedPipeline(this);
            registered = false;
        }
        cleanUpNativeState();
    }

    private void cleanUpNativeState() {
        if (computePipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(Vulkan.getVkDevice(), computePipeline, null);
            computePipeline = VK_NULL_HANDLE;
        }
        if (descriptorSets != null) {
            destroyDescriptorSets();
            descriptorSets = null;
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(Vulkan.getVkDevice(), pipelineLayout, null);
            pipelineLayout = VK_NULL_HANDLE;
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(Vulkan.getVkDevice(), descriptorSetLayout, null);
            descriptorSetLayout = VK_NULL_HANDLE;
        }
    }

    private static void validateLocalSize(String name, int x, int y, int z) throws IOException {
        var limits = DeviceManager.deviceProperties.limits();
        long invocations = (long) x * y * z;
        if (x > limits.maxComputeWorkGroupSize(0)
                || y > limits.maxComputeWorkGroupSize(1)
                || z > limits.maxComputeWorkGroupSize(2)
                || invocations > limits.maxComputeWorkGroupInvocations()) {
            throw new IOException("Compute program " + name + " local size exceeds Vulkan device limits.");
        }
    }

    private static void requireGraphicsQueueComputeSupport() throws IOException {
        int graphicsFamily = Queue.getQueueFamilies().graphicsFamily;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(DeviceManager.physicalDevice, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(DeviceManager.physicalDevice, count, families);
            if (graphicsFamily < 0 || graphicsFamily >= families.capacity()
                    || (families.get(graphicsFamily).queueFlags() & VK_QUEUE_COMPUTE_BIT) == 0) {
                throw new IOException("VulkanMod's graphics queue does not support compute dispatch.");
            }
        }
    }

}
