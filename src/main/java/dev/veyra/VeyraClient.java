package dev.veyra;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import dev.veyra.shader.ShaderPackRepository;
import dev.veyra.shader.VeyraRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VeyraClient implements ClientModInitializer {
    public static final String MOD_ID = "veyra";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ShaderPackRepository shaderPacks;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            throw new IllegalStateException("Veyra requires VulkanMod.");
        }

        LOGGER.info("Veyra initialized for Minecraft {} with VulkanMod {}.",
                FabricLoader.getInstance().getModContainer("minecraft").map(mod -> mod.getMetadata().getVersion()).orElse(null),
                FabricLoader.getInstance().getModContainer("vulkanmod").map(mod -> mod.getMetadata().getVersion()).orElse(null));

        shaderPacks = new ShaderPackRepository(FabricLoader.getInstance().getGameDir());
        shaderPacks.scan();
        VeyraRenderer.initialize(shaderPacks);
        LOGGER.info("Found {} shader pack(s) in {}.", shaderPacks.getPacks().size(), shaderPacks.getDirectory());
    }
}
