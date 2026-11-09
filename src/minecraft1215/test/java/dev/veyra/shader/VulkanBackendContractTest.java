package dev.veyra.shader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Verify required injection contracts against the pinned backend bytecode without loading a GPU. */
class VulkanBackendContractTest {
    @Test void customFragmentSourceCanBypassAllNativeJsonFallbacks() throws IOException {
        requiresCalls("net/vulkanmod/render/engine/VkGpuDevice", "compilePipeline",
                "net/vulkanmod/render/shader/ShaderLoadUtil", "getJsonConfig",
                "(Ljava/lang/String;Ljava/lang/String;)Lcom/google/gson/JsonObject;", 4);
        var handler = read("dev/veyra/mixin/LegacyPipelineBindingsMixin").methods.stream()
                .filter(method -> method.name.equals("veyra$useProvidedPackSources")).findFirst().orElseThrow();
        var wrapper = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/WrapOperation;")).findFirst().orElseThrow();
        var positions = (java.util.List<?>) wrapper.values.get(wrapper.values.indexOf("at") + 1);
        var at = (org.objectweb.asm.tree.AnnotationNode) positions.getFirst();
        assertTrue(at.values.contains("Lnet/vulkanmod/render/shader/ShaderLoadUtil;getJsonConfig(Ljava/lang/String;Ljava/lang/String;)Lcom/google/gson/JsonObject;"));
    }

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
        requiresCalls("net/minecraft/client/renderer/GameRenderer", "render",
                "com/mojang/blaze3d/systems/CommandEncoder", "clearDepthTexture",
                "(Lcom/mojang/blaze3d/textures/GpuTexture;D)V", 2);
        requiresCall("dev/veyra/mixin/GameRendererMixin", "veyra$finishWorldBeforeGui",
                "dev/veyra/shader/VeyraRenderer", "render", "(Lnet/minecraft/client/Minecraft;)V");
        requiresCall("dev/veyra/mixin/VkRenderPassMixin", "veyra$routeEntityPipeline",
                "dev/veyra/shader/VeyraRenderer", "isGuiRendering", "()Z");
    }

    @Test void terrainAtlasSamplerHookMatchesPinnedBackend() throws IOException {
        requiresCall("net/vulkanmod/render/chunk/WorldRenderer", "renderSectionLayer",
                "net/vulkanmod/vulkan/texture/VTextureSelector", "bindShaderTextures",
                "(Lnet/vulkanmod/vulkan/shader/Pipeline;)V");
        requiresCall("dev/veyra/mixin/WorldRendererMixin", "veyra$keepShaderPackAtlasSharp",
                "dev/veyra/vulkan/LegacySampler", "from",
                "(Lcom/mojang/blaze3d/textures/GpuTexture;)Ldev/veyra/vulkan/LegacySampler;");
        requiresCall("dev/veyra/mixin/WorldRendererMixin", "veyra$restoreAtlasSampler",
                "dev/veyra/vulkan/LegacySampler", "apply", "(Lcom/mojang/blaze3d/textures/GpuTexture;)V");
    }

    @Test void acquisitionResetOccursOnlyWhenAnImageIsAcquired() throws IOException {
        requiresCall("net/vulkanmod/vulkan/Renderer", "beginFrame", "org/lwjgl/vulkan/KHRSwapchain",
                "vkAcquireNextImageKHR", "(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I");
        var reset = read("dev/veyra/mixin/RendererSubmissionMixin").methods.stream()
                .filter(method -> method.name.equals("veyra$resetAcquisition")).findFirst().orElseThrow();
        var inject = reset.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        var positions = (java.util.List<?>) inject.values.get(inject.values.indexOf("at") + 1);
        var position = (org.objectweb.asm.tree.AnnotationNode) positions.getFirst();
        assertTrue(position.values.contains("Lorg/lwjgl/vulkan/KHRSwapchain;vkAcquireNextImageKHR(Lorg/lwjgl/vulkan/VkDevice;JJJJLjava/nio/IntBuffer;)I"));
    }

    @Test void presentationWaitUsesTheAcquiredImageAndExtraHandlesAreReleased() throws IOException {
        requiresCall("dev/veyra/mixin/RendererSubmissionMixin", "veyra$presentationSemaphoreByImage",
                "net/vulkanmod/vulkan/Renderer", "getCurrentImage", "()I");
        requiresCall("dev/veyra/mixin/RendererSubmissionMixin", "veyra$allocatePresentationSemaphores",
                "net/vulkanmod/vulkan/framebuffer/SwapChain", "getImagesNum", "()I");
        requiresCall("dev/veyra/mixin/RendererSubmissionMixin", "veyra$destroyExtraPresentationSemaphores",
                "org/lwjgl/vulkan/VK10", "vkDestroySemaphore",
                "(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V");
        var submit = read("net/vulkanmod/vulkan/Renderer").methods.stream()
                .filter(method -> method.name.equals("submitFrame")).findFirst().orElseThrow();
        int presentationReads = 0;
        for (var instruction : submit.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.getOpcode() == org.objectweb.asm.Opcodes.GETFIELD
                    && field.name.equals("renderFinishedSemaphores")) presentationReads++;
        }
        assertEquals(2, presentationReads, "Both signal and presentation waits must use the image semaphore");
    }

    @Test void cloudPreparationDoesNotDependOnRenderEntryPointArguments() throws IOException {
        var handler = read("dev/veyra/mixin/VulkanCloudRendererMixin").methods.stream()
                .filter(method -> method.name.equals("veyra$captureAndPrepareCloudMesh")).findFirst().orElseThrow();
        assertEquals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", handler.desc);
        assertEquals(1, read("net/vulkanmod/render/sky/CloudRenderer").methods.stream()
                .filter(method -> method.name.equals("renderClouds")).count());
    }

    @Test void legacyFramebufferCleanupKeepsBorrowedAttachments() throws IOException {
        requiresCall("net/vulkanmod/gl/VkGlFramebuffer", "cleanUp",
                "net/vulkanmod/vulkan/framebuffer/Framebuffer", "cleanUp", "(Z)V");
        int cleanups = 0;
        for (var method : read("dev/veyra/mixin/VulkanCleanupMixin").methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !call.name.equals("veyra$cleanUp")) continue;
                assertEquals(org.objectweb.asm.Opcodes.ICONST_0, call.getPrevious().getOpcode(),
                        "Cached framebuffer cleanup must not destroy borrowed texture attachments");
                cleanups++;
            }
        }
        assertEquals(1, cleanups);
    }

    @Test void uploadBarrierHookMatchesPinnedBackend() throws IOException {
        requiresCall("net/vulkanmod/vulkan/texture/ImageUtil", "copyBufferToImageCmd",
                "org/lwjgl/vulkan/VK10", "vkCmdCopyBufferToImage",
                "(Lorg/lwjgl/vulkan/VkCommandBuffer;JJILorg/lwjgl/vulkan/VkBufferImageCopy$Buffer;)V");
    }

    @Test void depthDependencyHooksMatchBothNativeBranches() throws IOException {
        requiresCalls("net/vulkanmod/vulkan/framebuffer/RenderPass", "createRenderPass",
                "org/lwjgl/vulkan/VkSubpassDependency", "srcStageMask",
                "(I)Lorg/lwjgl/vulkan/VkSubpassDependency;", 2);
        requiresCalls("net/vulkanmod/vulkan/framebuffer/RenderPass", "createRenderPass",
                "org/lwjgl/vulkan/VkSubpassDependency", "srcAccessMask",
                "(I)Lorg/lwjgl/vulkan/VkSubpassDependency;", 2);
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
        requiresCalls(owner, method, targetOwner, targetMethod, descriptor, 1);
    }
    private static void requiresCalls(String owner, String method, String targetOwner, String targetMethod,
                                      String descriptor, long count) throws IOException {
        ClassNode node = read(owner);
        long matches = node.methods.stream().filter(m -> m.name.equals(method)).flatMap(m -> {
            java.util.List<MethodInsnNode> calls = new java.util.ArrayList<>();
            for (var instruction : m.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
            return calls.stream();
        }).filter(call -> call.owner.equals(targetOwner) && call.name.equals(targetMethod) && call.desc.equals(descriptor)).count();
        assertEquals(count, matches, owner + "." + method + " injection target changed: " + targetMethod);
    }
    @Test void nativePipelineBlendAndFormatHooksExistExactlyOnce() throws IOException {
        String owner = "net/vulkanmod/vulkan/shader/GraphicsPipeline";
        assertTrue(read(owner).fields.stream().anyMatch(field -> field.name.equals("graphicsPipelines")
                && field.desc.equals("Lit/unimi/dsi/fastutil/objects/Object2LongMap;")));
        assertTrue(read("net/vulkanmod/vulkan/shader/Pipeline").fields.stream().anyMatch(field ->
                field.name.equals("PIPELINES") && field.desc.equals("Ljava/util/List;")));
        requiresCall(owner, "createGraphicsPipeline", "org/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo", "pAttachments",
                "(Lorg/lwjgl/vulkan/VkPipelineColorBlendAttachmentState$Buffer;)Lorg/lwjgl/vulkan/VkPipelineColorBlendStateCreateInfo;");
        requiresCall(owner, "createGraphicsPipeline", "org/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer", "renderPass",
                "(J)Lorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;");
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
