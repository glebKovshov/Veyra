package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Verify required injection contracts against the pinned backend bytecode without loading a GPU. */
class VulkanBackendContractTest {
    private static ClassNode read(String name) throws IOException {
        try (var input = VulkanBackendContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, "Pinned VulkanMod class absent: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
    private static void requiresCall(String owner, String method, String targetOwner, String targetMethod, String descriptor) throws IOException {
        ClassNode node = read(owner);
        long matches = node.methods.stream().filter(m -> m.name.equals(method)).flatMap(m -> {
            java.util.List<MethodInsnNode> calls = new java.util.ArrayList<>();
            for (var instruction : m.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
            return calls.stream();
        }).filter(call -> call.owner.equals(targetOwner) && call.name.equals(targetMethod) && call.desc.equals(descriptor)).count();
        assertEquals(1, matches, owner + "." + method + " injection target changed: " + targetMethod);
    }
    @Test void nativePipelineBlendAndFormatHooksExistExactlyOnce() throws IOException {
        String owner = "net/vulkanmod/vulkan/shader/GraphicsPipeline";
        assertTrue(read(owner).fields.stream().anyMatch(field -> field.name.equals("graphicsPipelines")
                && field.desc.equals("Lit/unimi/dsi/fastutil/objects/Object2LongMap;")));
        assertTrue(read("net/vulkanmod/vulkan/shader/Pipeline").fields.stream().anyMatch(field ->
                field.name.equals("PIPELINES") && field.desc.equals("Ljava/util/List;")));
        requiresCall(owner, "createGraphicsPipeline", "org/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo", "pAttachments",
                "(Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;)Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;");
        requiresCall(owner, "createGraphicsPipeline", "org/lwjgl/vulkan/VkPipelineRenderingCreateInfoKHR", "pColorAttachmentFormats",
                "(Ljava/nio/IntBuffer;)Lorg/lwjgl/vulkan/VkPipelineRenderingCreateInfoKHR;");
    }
    @Test void deviceFeatureHookAndNativePassFieldMatchPinnedBackend() throws IOException {
        requiresCall("net/vulkanmod/vulkan/device/DeviceManager", "createLogicalDevice", "org/lwjgl/vulkan/VkPhysicalDeviceFeatures", "logicOp",
                "(Z)Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures;");
        assertTrue(read("net/vulkanmod/vulkan/shader/PipelineState").fields.stream().anyMatch(field ->
                field.name.equals("renderPass") && field.desc.equals("Lnet/vulkanmod/vulkan/framebuffer/RenderPass;")));
        assertTrue(read("net/vulkanmod/vulkan/Renderer").fields.stream().anyMatch(field ->
                field.name.equals("boundFramebuffer") && field.desc.equals("Lnet/vulkanmod/vulkan/framebuffer/Framebuffer;")));
    }
    @Test void swapchainUsageHooksMatchPinnedBackend() throws IOException {
        requiresCall("net/vulkanmod/vulkan/framebuffer/SwapChain", "createSwapChain", "org/lwjgl/vulkan/VkSwapchainCreateInfoKHR", "imageUsage",
                "(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;");
        requiresCall("net/vulkanmod/vulkan/framebuffer/SwapChain", "createSwapChain", "net/vulkanmod/vulkan/texture/VulkanImage", "<init>",
                "(Ljava/lang/String;JIIIIIIJ)V");
    }
}
