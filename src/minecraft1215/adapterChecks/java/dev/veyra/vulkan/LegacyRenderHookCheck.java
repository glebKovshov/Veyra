package dev.veyra.vulkan;

import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

final class LegacyRenderHookCheck {
    static void run() throws IOException {
        assert LegacyAcquisition.succeeded(org.lwjgl.vulkan.VK10.VK_SUCCESS);
        assert LegacyAcquisition.succeeded(org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR);
        assert !LegacyAcquisition.succeeded(org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR);
        assert !LegacyAcquisition.succeeded(org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR);
        var handles = java.nio.LongBuffer.wrap(new long[]{11, 22, 33, 44});
        handles.position(1).limit(3);
        long[] copy = dev.veyra.smoke.LegacyShutdownDescriptors.copy(handles);
        assert java.util.Arrays.equals(copy, new long[]{22, 33});
        assert handles.position() == 1 && handles.limit() == 3;
        handles.put(1, 99);
        assert copy[0] == 22;
        assert dev.veyra.smoke.LegacyShutdownDescriptors.copy(null) == null;
        assert dev.veyra.smoke.LegacyShutdownDescriptors.copy(java.nio.LongBuffer.allocate(0)).length == 0;
        checkMainPassAttachments();
        requiresRenderCall("net/vulkanmod/mixin/render/target/MainTargetMixin", "getColorTexture",
                "net/vulkanmod/vulkan/pass/MainPass", "getColorAttachment()Lcom/mojang/blaze3d/textures/GpuTexture;");
        requiresRenderCall("dev/veyra/mixin/LegacyRenderTargetMixin", "veyra$getColorTextureView",
                "dev/veyra/mixin/LegacyRenderTargetMixin", "getColorTexture()Lcom/mojang/blaze3d/textures/GpuTexture;");
        requiresRenderCall("net/vulkanmod/vulkan/Renderer", "beginRenderPass",
                "net/vulkanmod/vulkan/pass/MainPass", "begin(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V");
        requires("net/vulkanmod/render/chunk/buffer/DrawBuffers",
                "getMask(Lnet/minecraft/world/phys/Vec3;Lnet/vulkanmod/render/chunk/RenderSection;)I");
        requires("net/vulkanmod/config/gui/VOptionScreen", "addButtons()V");
        requires("net/vulkanmod/vulkan/texture/ImageUtil", "copyImageToBuffer(Lnet/vulkanmod/vulkan/texture/VulkanImage;"
                + "Lnet/vulkanmod/vulkan/memory/buffer/Buffer;IIIIIIII)V");
        requiresRenderCall("net/vulkanmod/render/vertex/TerrainBuilder", "<init>",
                "net/vulkanmod/render/vertex/TerrainBufferBuilder", "<init>(IILnet/vulkanmod/render/vertex/VertexBuilder;)V");
        requires("net/minecraft/client/renderer/entity/EntityRenderDispatcher", "render("
                + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V");
        requires("net/vulkanmod/render/engine/VkCommandEncoder", LegacyRenderHooks.CREATE_RENDER_PASS);
        requires("net/vulkanmod/render/engine/VkRenderPass", LegacyRenderHooks.BIND_SAMPLER);
        requires("net/vulkanmod/render/engine/VkCommandEncoder", "trySetup(Lnet/vulkanmod/render/engine/VkRenderPass;)Z");
        requires("net/vulkanmod/render/engine/VkCommandEncoder", "clearColorTexture(Lcom/mojang/blaze3d/textures/GpuTexture;I)V");
        requires("net/vulkanmod/render/engine/VkRenderPass", "setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V");
        requires("net/vulkanmod/interfaces/shader/ExtendedRenderPipeline", "getPipeline()Lnet/vulkanmod/vulkan/shader/Pipeline;");
        requires("net/minecraft/client/renderer/LightTexture", "getTarget()Lcom/mojang/blaze3d/textures/GpuTexture;");
        requires("net/vulkanmod/render/chunk/WorldRenderer", "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
        requires("net/vulkanmod/vulkan/shader/GraphicsPipeline", LegacyRenderHooks.CREATE_SHADER_MODULES);
        requires("net/vulkanmod/render/PipelineManager", "getTerrainShader(Lnet/vulkanmod/render/vertex/TerrainRenderType;)Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;");
        requires("net/vulkanmod/render/PipelineManager", "getCloudsPipeline()Lnet/vulkanmod/vulkan/shader/GraphicsPipeline;");
        requires("net/vulkanmod/vulkan/texture/VulkanImage", "createImageView(JIII)J");
        requires("net/vulkanmod/render/engine/VkGpuTexture", "<init>(Ljava/lang/String;Lcom/mojang/blaze3d/textures/TextureFormat;IIIILnet/vulkanmod/gl/VkGlTexture;)V");
        requires("net/minecraft/client/renderer/entity/EntityRenderer", LegacyRenderHooks.ENTITY_RENDER);
        requires("net/minecraft/client/renderer/blockentity/BlockEntityRenderer", LegacyRenderHooks.BLOCK_ENTITY_RENDER);
        requires("net/minecraft/client/renderer/ItemInHandRenderer", LegacyRenderHooks.RENDER_ARM_WITH_ITEM);
        requires("net/minecraft/client/renderer/item/ItemStackRenderState", LegacyRenderHooks.ITEM_STACK_RENDER);
        requires("net/minecraft/client/renderer/MultiBufferSource$BufferSource", "getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;");
        requires("net/minecraft/client/renderer/RenderType", "getRenderPipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;");
        requires("net/minecraft/client/renderer/GameRenderer", "render(Lnet/minecraft/client/DeltaTracker;Z)V");
        requires("net/minecraft/client/renderer/GameRenderer", "renderItemInHand(Lnet/minecraft/client/Camera;FLorg/joml/Matrix4f;)V");
        requires("com/mojang/blaze3d/systems/CommandEncoder", LegacyRenderHooks.CLEAR_DEPTH_TEXTURE);
        requires("net/minecraft/client/renderer/LevelRenderer", "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
                + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;"
                + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
        requires("net/vulkanmod/render/chunk/WorldRenderer", "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;DDD"
                + "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;F)V");
        requiresRenderCall("net/minecraft/client/renderer/LevelRenderer", "renderLevel",
                "net/minecraft/client/renderer/FogRenderer", "computeFogColor(Lnet/minecraft/client/Camera;F"
                        + "Lnet/minecraft/client/multiplayer/ClientLevel;IF)Lorg/joml/Vector4f;");
        requiresRenderCall("net/minecraft/client/renderer/LevelRenderer", "renderLevel",
                "com/mojang/blaze3d/framegraph/FrameGraphBuilder", "execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
                        + "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V");
        requires("net/minecraft/client/renderer/LevelRenderer", "prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
        requiresRenderCall("net/minecraft/client/renderer/GameRenderer", "renderLevel",
                "net/minecraft/client/renderer/LevelRenderer", "prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V");
        checkGuiFrameOrder();
        requires("net/minecraft/client/renderer/item/ItemModelResolver", "updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;"
                + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/world/entity/LivingEntity;I)V");
        requiresRenderCall("net/minecraft/client/renderer/entity/EntityRenderDispatcher", "render",
                "net/minecraft/client/renderer/entity/EntityRenderer", LegacyRenderHooks.ENTITY_RENDER);
        requiresRenderCall("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher", "setupAndRender",
                "net/minecraft/client/renderer/blockentity/BlockEntityRenderer", LegacyRenderHooks.BLOCK_ENTITY_RENDER);
        checkShaderCleanupOrder();
        checkDescriptorBufferHooks();
        requires("net/vulkanmod/vulkan/shader/converter/GlslConverter", "process(Lcom/mojang/blaze3d/vertex/VertexFormat;Ljava/lang/String;Ljava/lang/String;)V");
        requires("net/vulkanmod/vulkan/shader/converter/GlslConverter", "getSamplerList()Ljava/util/List;");
        requires("net/vulkanmod/vulkan/shader/converter/GlslConverter", "getVshConverted()Ljava/lang/String;");
        requires("net/vulkanmod/vulkan/shader/converter/GlslConverter", "getFshConverted()Ljava/lang/String;");
        requiresRenderCall("net/vulkanmod/render/engine/VkGpuDevice", "compilePipeline",
                "net/vulkanmod/vulkan/shader/Pipeline$Builder", "setUniforms(Ljava/util/List;Ljava/util/List;)V");
        requiresRenderCall("net/vulkanmod/render/engine/VkGpuDevice", "compilePipeline",
                "net/vulkanmod/interfaces/shader/ExtendedRenderPipeline", "setupUniformSuppliers(Lnet/vulkanmod/vulkan/shader/descriptor/UBO;)V");
        requiresRenderCall("net/vulkanmod/vulkan/shader/converter/UniformParser", "createUBO",
                "net/vulkanmod/vulkan/shader/layout/Uniform", "createUniformInfo(Ljava/lang/String;Ljava/lang/String;)Lnet/vulkanmod/vulkan/shader/layout/Uniform$Info;");
        System.out.println("Legacy native render-hook descriptors verified");
    }
    private static void checkDescriptorBufferHooks() throws IOException {
        requires("net/vulkanmod/vulkan/shader/Pipeline", "destroyDescriptorSets()V");
        requires("net/vulkanmod/vulkan/shader/Pipeline", "createShaderModule(Ljava/nio/ByteBuffer;)J");
        requires("net/vulkanmod/vulkan/shader/Pipeline$DescriptorSets", "bindSets(Lorg/lwjgl/vulkan/VkCommandBuffer;Lnet/vulkanmod/vulkan/memory/buffer/UniformBuffer;I)V");
        for (String method : new String[]{"needsUpdate", "updateDescriptorSet"})
            requiresRenderCall("net/vulkanmod/vulkan/shader/Pipeline$DescriptorSets", method,
                    "net/vulkanmod/vulkan/memory/buffer/UniformBuffer", "getId()J");
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(
                "net/vulkanmod/vulkan/shader/Pipeline$DescriptorSets.class")) {
            var node = new ClassNode();
            new ClassReader(java.util.Objects.requireNonNull(stream)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var pool = node.methods.stream().filter(method -> method.name.equals("createDescriptorPool")).findFirst().orElseThrow();
            int types = 0, increments = 0;
            for (var instruction : pool.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("org/lwjgl/vulkan/VkDescriptorPoolSize") && call.name.equals("type")) types++;
                if (instruction instanceof org.objectweb.asm.tree.IincInsnNode increment && increment.var == 4) increments++;
            }
            assert types == 2 && increments == 2 : "Native descriptor pool loop/local changed";
        }
    }
    private static void checkGuiFrameOrder() throws IOException {
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(
                "net/minecraft/client/renderer/GameRenderer.class")) {
            if (stream == null) throw new AssertionError("Missing native game renderer");
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var render = node.methods.stream().filter(method -> method.name.equals("render")
                    && method.desc.equals("(Lnet/minecraft/client/DeltaTracker;Z)V")).findFirst().orElseThrow();
            boolean world = false, guiDepth = false, gui = false;
            for (var instruction : render.instructions) {
                if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)) continue;
                if (call.owner.equals(node.name) && call.name.equals("renderLevel")) world = true;
                if (call.owner.equals("com/mojang/blaze3d/systems/CommandEncoder")
                        && (call.name + call.desc).equals(LegacyRenderHooks.CLEAR_DEPTH_TEXTURE) && !guiDepth) {
                    assert world && !gui : "GUI boundary no longer follows the world stage";
                    guiDepth = true;
                }
                if (call.owner.equals("net/minecraft/client/gui/Gui") && call.name.equals("render")) {
                    assert guiDepth : "GUI draws before the selected shader-frame boundary";
                    gui = true;
                }
            }
            assert world && guiDepth && gui;
        }
    }
    private static void requiresRenderCall(String owner, String methodName, String target, String selector) throws IOException {
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing native class " + owner);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            int calls = 0;
            for (var method : node.methods) {
                if (!method.name.equals(methodName)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals(target) && (call.name + call.desc).equals(selector)) calls++;
                }
            }
            assert calls == 1 : "Expected exactly one renderer redirect target: " + owner + "." + methodName;
        }
    }
    private static void checkShaderCleanupOrder() throws IOException {
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(
                "net/vulkanmod/vulkan/shader/GraphicsPipeline.class")) {
            if (stream == null) throw new AssertionError("Missing native graphics pipeline");
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            var constructor = node.methods.stream().filter(method -> method.name.equals("<init>"))
                    .findFirst().orElseThrow();
            boolean descriptorLayout = false, pipelineLayout = false, modules = false, pools = false;
            for (var instruction : constructor.instructions) {
                if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)) continue;
                if (call.name.equals("createDescriptorSetLayout")) descriptorLayout = true;
                if (call.name.equals("createPipelineLayout")) pipelineLayout = true;
                if (call.name.equals("createShaderModules")) {
                    assert descriptorLayout && pipelineLayout && !pools;
                    modules = true;
                }
                if (call.name.equals("createDescriptorSets")) { assert modules; pools = true; }
            }
            assert modules && pools : "Native construction lifecycle changed";
        }
    }
    private static void checkMainPassAttachments() throws IOException {
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(
                "net/vulkanmod/vulkan/pass/DefaultMainPass.class")) {
            if (stream == null) throw new AssertionError("Missing native main pass");
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assert node.interfaces.contains("net/vulkanmod/vulkan/pass/MainPass");
            assert node.fields.stream().anyMatch(field -> field.name.equals("mainFramebuffer")
                    && field.desc.equals("Lnet/vulkanmod/vulkan/framebuffer/Framebuffer;"));
            assert node.fields.stream().anyMatch(field -> field.name.equals("mainTarget")
                    && field.desc.equals("Lcom/mojang/blaze3d/pipeline/RenderTarget;"));
        }
        requiresRenderCall("net/vulkanmod/vulkan/pass/DefaultMainPass", "<init>",
                "net/minecraft/client/Minecraft", "getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;");
        requiresRenderCall("net/vulkanmod/vulkan/pass/DefaultMainPass", "<init>",
                "net/vulkanmod/vulkan/Renderer", "getSwapChain()Lnet/vulkanmod/vulkan/framebuffer/SwapChain;");
        requires("com/mojang/blaze3d/pipeline/RenderTarget", "getDepthTexture()Lcom/mojang/blaze3d/textures/GpuTexture;");
    }

    private static void requires(String owner, String selector) throws IOException {
        try (var stream = LegacyRenderHookCheck.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing native class " + owner);
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assert node.methods.stream().anyMatch(method -> (method.name + method.desc).equals(selector))
                    : "Missing native hook " + owner + "." + selector;
        }
    }
}
