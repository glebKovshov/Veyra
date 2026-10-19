package dev.veyra.vulkan;

import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/** Borrowed geometry attachments, with native MRT support for both VulkanMod rendering modes. */
public final class VeyraMrtFramebuffer extends Framebuffer implements AutoCloseable {
    private final List<VulkanImage> colors;
    private final VulkanImage depth;
    private final MultiPass pass;
    private long framebuffer;
    private boolean closed;

    public VeyraMrtFramebuffer(List<VulkanImage> colors, VulkanImage depth) {
        super();
        if (colors.isEmpty()) throw new IllegalArgumentException("MRT needs a color attachment.");
        this.colors = List.copyOf(colors);
        this.depth = depth;
        width = colors.getFirst().width;
        height = colors.getFirst().height;
        format = colors.getFirst().format;
        depthFormat = depth == null ? VK_FORMAT_UNDEFINED : depth.format;
        for (int i = 0; i < colors.size(); i++) {
            VulkanImage image = colors.get(i);
            if (image.width != width || image.height != height)
                throw new IllegalArgumentException("Geometry color slot " + i + " is " + image.width + "x" + image.height + "; expected " + width + "x" + height + ".");
            if ((image.usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0)
                throw new IllegalArgumentException("Geometry color slot " + i + " lacks color-attachment usage (reported flags " + image.usage + ").");
        }
        if (depth != null && (depth.width != width || depth.height != height))
            throw new IllegalArgumentException("Geometry MRT depth dimensions differ from its color attachments.");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, properties);
            if (colors.size() > properties.limits().maxColorAttachments())
                throw new IllegalArgumentException("The Vulkan device supports fewer geometry color attachments than this program requires.");
        }
        pass = new MultiPass(this);
    }

    public RenderPass renderPass() { return pass; }
    public List<VulkanImage> colors() { return colors; }
    @Override public VulkanImage getColorAttachment() { return colors.getFirst(); }
    @Override public VulkanImage getDepthAttachment() { return depth; }
    @Override public long getColorAttachmentView() { return colors.getFirst().getImageView(); }
    @Override public long getDepthImageView() { return depth.getImageView(); }

    @Override public void beginRenderPass(VkCommandBuffer commands, RenderPass ignored, MemoryStack stack) {
        if (closed) throw new IllegalStateException("The MRT framebuffer has been retired.");
        transitionAttachments(commands, stack);
        if (Vulkan.DYNAMIC_RENDERING) pass.beginDynamicRendering(commands, stack);
        else {
            if (framebuffer == 0) {
                var views = stack.mallocLong(colors.size() + (depth == null ? 0 : 1));
                for (VulkanImage image : colors) views.put(image.getImageView());
                if (depth != null) views.put(depth.getImageView());
                views.flip();
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack).sType$Default()
                        .renderPass(pass.getId()).width(width).height(height).layers(1).pAttachments(views);
                var handle = stack.mallocLong(1);
                Vulkan.checkResult(vkCreateFramebuffer(Vulkan.getVkDevice(), info, null, handle), "Create Veyra MRT framebuffer");
                framebuffer = handle.get(0);
            }
            pass.beginRenderPass(commands, framebuffer, stack);
        }
    }

    private void transitionAttachments(VkCommandBuffer commands, MemoryStack stack) {
        for (VulkanImage image : colors) image.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        if (depth != null) depth.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
    }

    @Override public void cleanUp() { close(); }
    @Override public void cleanUp(boolean ignored) { close(); }
    @Override public void close() {
        if (closed) return;
        closed = true;
        long handle = framebuffer;
        var device = Vulkan.getVkDevice();
        if (handle != 0) MemoryManager.getInstance().addFrameOp(() -> vkDestroyFramebuffer(device, handle, null));
        pass.cleanUp();
        framebuffer = 0;
        // Images belong to Minecraft/Veyra color targets, not this borrowed framebuffer.
    }

    private static final class MultiPass extends RenderPass {
        private final VeyraMrtFramebuffer owner;
        private final long handle;
        private boolean closed;

        MultiPass(VeyraMrtFramebuffer owner) {
            super(owner, new AttachmentInfo(AttachmentInfo.Type.COLOR, owner.getFormat())
                    .setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE),
                    owner.depth == null ? null : new AttachmentInfo(AttachmentInfo.Type.DEPTH, owner.getDepthFormat())
                            .setOps(VK_ATTACHMENT_LOAD_OP_LOAD, VK_ATTACHMENT_STORE_OP_STORE));
            this.owner = owner;
            // The public backend constructor creates a single-target compatibility pass.
            // It is never submitted; retire it and keep our own multi-attachment handle.
            super.cleanUp();
            handle = Vulkan.DYNAMIC_RENDERING ? 0 : create(owner);
        }

        private static long create(VeyraMrtFramebuffer owner) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                int count = owner.colors.size();
                VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(count + (owner.depth == null ? 0 : 1), stack);
                VkAttachmentReference.Buffer colorRefs = VkAttachmentReference.calloc(count, stack);
                for (int i = 0; i < count; i++) {
                    attachments.get(i).format(owner.colors.get(i).format).samples(VK_SAMPLE_COUNT_1_BIT)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                            .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL).finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                    colorRefs.get(i).attachment(i).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                }
                VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                        .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).pColorAttachments(colorRefs).colorAttachmentCount(count);
                if (owner.depth != null) {
                    attachments.get(count).format(owner.depth.format).samples(VK_SAMPLE_COUNT_1_BIT)
                            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                            .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
                    subpass.pDepthStencilAttachment(VkAttachmentReference.calloc(stack).attachment(count)
                            .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL));
                }
                VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(1, stack);
                int stages = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
                int writes = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                dependencies.srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0).srcStageMask(stages).dstStageMask(stages)
                        .srcAccessMask(writes).dstAccessMask(writes | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT)
                        .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
                VkRenderPassCreateInfo info = VkRenderPassCreateInfo.calloc(stack).sType$Default()
                        .pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);
                var result = stack.mallocLong(1);
                Vulkan.checkResult(vkCreateRenderPass(Vulkan.getVkDevice(), info, null, result), "Create Veyra MRT render pass");
                return result.get(0);
            }
        }

        @Override public long getId() { return handle; }
        @Override public void beginRenderPass(VkCommandBuffer commands, long framebuffer, MemoryStack stack) {
            VkRenderPassBeginInfo info = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                    .renderPass(handle).framebuffer(framebuffer);
            info.renderArea().offset().set(0, 0);
            info.renderArea().extent().set(owner.width, owner.height);
            vkCmdBeginRenderPass(commands, info, VK_SUBPASS_CONTENTS_INLINE);
            Renderer.getInstance().setBoundRenderPass(this);
        }

        @Override public void beginDynamicRendering(VkCommandBuffer commands, MemoryStack stack) {
            VkRenderingAttachmentInfo.Buffer attachments = VkRenderingAttachmentInfo.calloc(owner.colors.size(), stack);
            for (int i = 0; i < owner.colors.size(); i++) attachments.get(i)
                    .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                    .imageView(owner.colors.get(i).getImageView()).imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo info = VkRenderingInfo.calloc(stack).sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_INFO_KHR)
                    .layerCount(1).pColorAttachments(attachments);
            info.renderArea().offset().set(0, 0);
            info.renderArea().extent().set(owner.width, owner.height);
            if (owner.depth != null) info.pDepthAttachment(VkRenderingAttachmentInfo.calloc(stack)
                    .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_RENDERING_ATTACHMENT_INFO_KHR)
                    .imageView(owner.depth.getImageView()).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK_ATTACHMENT_STORE_OP_STORE));
            KHRDynamicRendering.vkCmdBeginRenderingKHR(commands, info);
            Renderer.getInstance().setBoundRenderPass(this);
        }

        @Override public void endRenderPass(VkCommandBuffer commands) {
            if (Vulkan.DYNAMIC_RENDERING) KHRDynamicRendering.vkCmdEndRenderingKHR(commands);
            else vkCmdEndRenderPass(commands);
            Renderer.getInstance().setBoundRenderPass(null);
        }

        @Override public void cleanUp() {
            if (closed) return;
            closed = true;
            if (handle != 0) {
                var device = Vulkan.getVkDevice();
                MemoryManager.getInstance().addFrameOp(() -> vkDestroyRenderPass(device, handle, null));
            }
        }
    }
}
