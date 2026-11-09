package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Verify required injection contracts against the pinned backend bytecode without loading a GPU. */
class VulkanBackendContractTest {
    @Test void computeDescriptorsUseTheirOwnBindPoint() throws IOException {
        requiresCall("dev/veyra/shader/VeyraComputePipeline", "bindForDispatch",
                "dev/veyra/mixin/accessor/NativeDescriptorSetsAccessor", "veyra$bindSets",
                "(Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/memory/buffer/UniformBuffer;I)V");
        var method = read("dev/veyra/shader/VeyraComputePipeline").methods.stream()
                .filter(value -> value.name.equals("bindForDispatch")).findFirst().orElseThrow();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            assertNotEquals("bindDescriptorSets", call.name, "Pipeline overload hardcodes the graphics bind point");
            if (call.name.equals("veyra$bindSets")) assertEquals(org.objectweb.asm.Opcodes.ICONST_1,
                    call.getPrevious().getOpcode(), "Descriptor binding must use VK_PIPELINE_BIND_POINT_COMPUTE");
        }
    }
    @Test void geometryTargetsAreInitializedBeforeWaitingForShadows() throws IOException {
        var phase = read("dev/veyra/shader/VeyraRenderer").methods.stream()
                .filter(method -> method.name.equals("renderPhase")).findFirst().orElseThrow();
        boolean allocated = false;
        boolean cleared = false;
        boolean shadowCheck = false;
        for (var instruction : phase.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.name.equals("ensureColorTargets")) allocated = true;
            if (call.name.equals("clearColorTargetBuffers")) cleared = true;
            if (call.name.equals("hasCompletedShadowMap")) {
                assertTrue(allocated && cleared, "Geometry initialization must precede the shadow readiness return");
                shadowCheck = true;
            }
        }
        assertTrue(shadowCheck);
    }

    @Test void pipelineSelectionChecksReadinessAndWorldFramePreparesTextures() throws IOException {
        requiresCall("dev/veyra/mixin/VkRenderPassMixin", "veyra$routeEntityPipeline",
                "dev/veyra/shader/VeyraRenderer", "shaderPackTexturesReady", "()Z");
        requiresCall("dev/veyra/shader/VeyraRenderer", "captureFrameMatrices",
                "dev/veyra/shader/VeyraRenderer", "prepareShaderPackTextures", "()Z");
        var readiness = read("dev/veyra/shader/VeyraRenderer").methods.stream()
                .filter(method -> method.name.equals("shaderPackTexturesReady")).findFirst().orElseThrow();
        for (var instruction : readiness.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.startsWith("dev/veyra/vulkan/"), "Readiness check must not touch GPU resources");
            }
        }
    }

    @Test void guiRenderingScopeMatchesMinecraftEntryPoint() throws IOException {
        requiresCall("net/minecraft/client/renderer/GameRenderer", "render",
                "net/minecraft/client/gui/render/GuiRenderer", "render",
                "(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V");
        requiresCall("dev/veyra/mixin/VkRenderPassMixin", "veyra$routeEntityPipeline",
                "dev/veyra/shader/VeyraRenderer", "isGuiRendering", "()Z");
    }

    @Test void terrainAtlasSamplerHookMatchesPinnedBackend() throws IOException {
        requiresCall("net/vulkanmod/render/chunk/WorldRenderer", "renderSectionLayer",
                "net/vulkanmod/vulkan/texture/VulkanImage", "setSampler", "(J)V");
    }

    @Test void cloudPreparationDoesNotDependOnRenderEntryPointArguments() throws IOException {
        var handler = read("dev/veyra/mixin/VulkanCloudRendererMixin").methods.stream()
                .filter(method -> method.name.equals("veyra$captureAndPrepareCloudMesh")).findFirst().orElseThrow();
        assertEquals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", handler.desc);
        assertEquals(1, read("net/vulkanmod/render/sky/CloudRenderer").methods.stream()
                .filter(method -> method.name.equals("renderClouds")).count());
    }

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
