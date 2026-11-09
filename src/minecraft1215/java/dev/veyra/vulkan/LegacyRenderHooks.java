package dev.veyra.vulkan;

public final class LegacyRenderHooks {
    private LegacyRenderHooks() { }
    public static final String CREATE_RENDER_PASS = "createRenderPass("
            + "Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalInt;"
            + "Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/util/OptionalDouble;)"
            + "Lcom/mojang/blaze3d/systems/RenderPass;";
    public static final String BIND_SAMPLER = "bindSampler(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTexture;)V";
    public static final String CREATE_SHADER_MODULES = "createShaderModules(Lnet/vulkanmod/vulkan/shader/SPIRVUtils$SPIRV;"
            + "Lnet/vulkanmod/vulkan/shader/SPIRVUtils$SPIRV;)V";
    public static final String ENTITY_RENDER = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    public static final String BLOCK_ENTITY_RENDER = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/phys/Vec3;)V";
    public static final String RENDER_ARM_WITH_ITEM = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
            + "Lnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    public static final String ITEM_STACK_RENDER = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";
    public static final String CLEAR_DEPTH_TEXTURE = "clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V";
}
