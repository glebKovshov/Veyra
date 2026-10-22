package dev.veyra.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.*;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercise a real client/world and measure the fixture's shader output against a baseline. */
public final class GraphicsSmoke {
    private static int ticks, worldTicks, stage, activePixels, baselineAt, disabledPixels, reenabledShadowMrtPixels;
    private static int lifecycleInitialWidth, lifecycleInitialHeight;
    private static boolean pending, finished, requested, captureActive;
    private static Runnable completion;
    private static final Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
    private static boolean entityFixtureStarted, vanillaBaseline, thirdPartyShaderpackFixture, thirdPartyScenePrepared,
            thirdPartyProfileApplied, cloudFixture, cloudSceneColorFixture,
            geometryViewSpaceFixture,
            geometryMidTexCoordFixture, terrainMaterialFixture, terrainVertexExecutionFixture,
            terrainVertexVaryingsFixture, waterMaterialFixture,
            entityMrtFixture, entityColorFeedbackFixture, currentRenderedItemFixture, glintVertexFixture,
            glintMrtFixture, shadowColorMrtFixture, shadowColorMrtLifecycleFixture, basicVertexFixture, basicCustomImageFixture, entityCustomImageFixture,
            compositeCustomImageFixture,
            skyBasicVertexFixture, skyTexturedStageFixture,
            beaconBeamFixture, texturedDefaultAttributesFixture, blockVertexFixture,
            damagedBlockVertexFixture, handVertexFixture, weatherVertexFixture, particleVertexFixture,
            storageBufferFixture;
    private static volatile net.minecraft.core.BlockPos damagedBlockPosition;
    private static ShaderPack shadowMrtPack;
    private static int activeOppositeMidTexCoordPixels, activeCurrentRenderedItemGreenPixels,
            disabledCurrentRenderedItemGreenPixels, activeGlintGreenPixels, disabledGlintGreenPixels,
            activeGlintMrtPixels, activeBasicGreenPixels, disabledBasicGreenPixels, activeBasicMrtPixels,
            disabledBasicMrtPixels, activeBeaconBeamGreenPixels, disabledBeaconBeamGreenPixels,
            activeBeaconBeamMrtPixels, disabledBeaconBeamMrtPixels,
            activeEntityCustomImageGreenPixels, disabledEntityCustomImageGreenPixels,
            activeSkySunPixels, activeSkyMoonPixels, disabledSkySunPixels, disabledSkyMoonPixels;
    private static String thirdPartyProfile = "";
    private static String skipReason = "";
    private static int thirdPartyNonBlackPixels, thirdPartyColorBuckets;
    private static volatile Throwable entityFixtureFailure;
    private GraphicsSmoke() { }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || finished) return;
        try {
            if (completion != null) {
                Runnable ready = completion; completion = null; ready.run();
                if (finished) return;
            }
            if (client.level != null && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Resuming the unattended validation world from PauseScreen");
                client.setScreen(null);
            }
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the validation world/readback; screen=" + client.screen);
            if (client.level == null || client.player == null || client.screen != null || pending) return;
            worldTicks++;
            String fixture = Files.readString(client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt"));
            vanillaBaseline = fixture.equals("vanilla-baseline");
            thirdPartyShaderpackFixture = fixture.equals("third-party-shaderpack");
            if (thirdPartyShaderpackFixture && !thirdPartyProfileApplied) {
                thirdPartyProfile = Files.readString(client.gameDirectory.toPath()
                        .resolve("veyra/shaderpack-profile.txt")).trim();
                ShaderPack pack = VeyraRenderer.selectedPack().orElseThrow(
                        () -> new IllegalStateException("The third-party shaderpack was not selected at startup."));
                if (thirdPartyProfile.isEmpty()) throw new IllegalStateException("The third-party shaderpack profile is empty.");
                var profile = pack.readOptions().profiles().get(thirdPartyProfile);
                if (profile == null || profile.isEmpty()) {
                    throw new IllegalStateException("Shaderpack profile was not found or has no options: " + thirdPartyProfile);
                }
                String applied = VeyraRenderer.applyOptions(pack, profile);
                if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty()) {
                    throw new IllegalStateException("Could not apply " + thirdPartyProfile + " profile: " + applied
                            + " (" + VeyraRenderer.getLastError() + ")");
                }
                ShaderPack configured = VeyraRenderer.selectedPack().orElseThrow();
                var effective = configured.readOptions().effective(configured.configuration().options());
                for (var option : profile.entrySet()) {
                    if (!option.getValue().equals(effective.get(option.getKey()))) {
                        throw new IllegalStateException("Profile option did not take effect: " + option.getKey());
                    }
                }
                thirdPartyProfileApplied = true;
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Applied real shaderpack profile {} with {} options from {}", thirdPartyProfile,
                        profile.size(), configured.name());
            }
            cloudSceneColorFixture = fixture.equals("cloud-colortex0");
            cloudFixture = cloudSceneColorFixture || fixture.equals("cloud-texture-inputs")
                    || fixture.equals("cloud-model-view") || fixture.equals("cloud-custom-image");
            geometryViewSpaceFixture = fixture.equals("geometry-view-space");
            geometryMidTexCoordFixture = fixture.equals("geometry-mid-texcoord");
            terrainMaterialFixture = fixture.equals("terrain-block-id");
            terrainVertexExecutionFixture = fixture.equals("terrain-vertex-execution");
            terrainVertexVaryingsFixture = fixture.equals("terrain-vertex-varyings") || fixture.equals("custom-image-compute");
            waterMaterialFixture = fixture.equals("water-block-id");
            entityMrtFixture = fixture.equals("entity-mrt");
            entityColorFeedbackFixture = fixture.equals("entity-colortex-feedback");
            currentRenderedItemFixture = fixture.equals("current-rendered-item-id");
            glintVertexFixture = fixture.equals("glint-vertex-execution");
            glintMrtFixture = fixture.equals("glint-mrt-outputs");
            shadowColorMrtLifecycleFixture = fixture.equals("shadow-color-mrt-lifecycle");
            shadowColorMrtFixture = fixture.equals("shadow-color-mrt") || shadowColorMrtLifecycleFixture;
            basicCustomImageFixture = fixture.equals("basic-custom-image");
            entityCustomImageFixture = fixture.equals("entity-custom-image");
            compositeCustomImageFixture = fixture.equals("composite-custom-image");
            basicVertexFixture = fixture.equals("basic-vertex-execution") || basicCustomImageFixture;
            skyBasicVertexFixture = fixture.equals("skybasic-vertex-execution");
            skyTexturedStageFixture = fixture.equals("skytextured-stage-variants");
            beaconBeamFixture = fixture.equals("beaconbeam-vertex-execution");
            texturedDefaultAttributesFixture = fixture.equals("textured-default-vertex-attributes");
            weatherVertexFixture = fixture.equals("weather-vertex-execution");
            particleVertexFixture = fixture.equals("particles-translucent-vertex-execution");
            blockVertexFixture = fixture.equals("block-vertex-execution");
            damagedBlockVertexFixture = fixture.equals("damagedblock-vertex-execution");
            handVertexFixture = fixture.equals("hand-vertex-execution");
            storageBufferFixture = fixture.equals("storage-buffer-shadow");
            boolean shadowVoxelImageFixture = fixture.equals("custom-image-compute")
                    || fixture.equals("cloud-custom-image") || fixture.equals("basic-custom-image")
                    || fixture.equals("entity-custom-image") || fixture.equals("composite-custom-image");
            if (storageBufferFixture && !VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled) {
                skipReason = "vertexPipelineStoresAndAtomics unavailable";
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Skipping storage-buffer fixture: {}", skipReason);
                finish(client, null, -1);
                return;
            }
            if (shadowVoxelImageFixture && !VeyraDeviceFeatures.supportsShadowVoxelWrites()) {
                skipReason = !VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled
                        ? "vertexPipelineStoresAndAtomics unavailable"
                        : "shaderStorageImageExtendedFormats unavailable";
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Skipping shadow-voxel custom-image fixture: {}", skipReason);
                finish(client, null, -1);
                return;
            }
            if (texturedDefaultAttributesFixture && worldTicks == 1) {
                var border = client.level.getWorldBorder();
                border.setCenter(client.player.getX(), client.player.getZ());
                border.setSize(4.0);
            }
            if (particleVertexFixture && stage == 0 && worldTicks >= 40 && worldTicks < 160
                    && worldTicks % 2 == 0) {
                var center = client.player.getEyePosition().add(client.player.getLookAngle().scale(3.0));
                for (int index = 0; index < 4; index++) {
                    var position = center.add((index - 1.5) * 0.2, (index % 2) * 0.2, 0.0);
                    client.level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, true, true,
                            position.x, position.y, position.z, 0.0, 0.0, 0.0);
                }
            }
            boolean itemFixture = currentRenderedItemFixture || glintVertexFixture || glintMrtFixture
                    || handVertexFixture;
            boolean entityFixture = itemFixture || fixture.equals("specular-entity")
                    || fixture.equals("shadow-color-entity") || shadowColorMrtFixture
                    || fixture.equals("water-shadow-depth-entity")
                    || entityColorFeedbackFixture || entityMrtFixture || basicVertexFixture
                    || fixture.equals("entity-vertex-execution") || blockVertexFixture || entityCustomImageFixture
                    || storageBufferFixture;
            if ((entityFixture || beaconBeamFixture || damagedBlockVertexFixture) && !entityFixtureStarted && worldTicks >= 20
                    && client.getSingleplayerServer() != null) {
                entityFixtureStarted = true;
                var server = client.getSingleplayerServer();
                // Initial player spawn is randomized around the world's spawn point.
                // Place subjects along the fixed 45-degree camera direction instead.
                double playerX = client.player.getX(), playerY = client.player.getY(), playerZ = client.player.getZ();
                server.execute(() -> {
                    try {
                        if (damagedBlockVertexFixture) {
                            damagedBlockPosition = net.minecraft.core.BlockPos.containing(
                                    playerX - 4.0, playerY - 1.0, playerZ + 4.0);
                            server.overworld().setBlock(damagedBlockPosition,
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                    "Placed damaged-block vertex fixture at {}", damagedBlockPosition);
                        } else if (beaconBeamFixture) {
                            var beacon = net.minecraft.core.BlockPos.containing(playerX - 4.0, playerY, playerZ + 4.0);
                            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                                server.overworld().setBlock(beacon.below().offset(x, 0, z),
                                        net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3);
                            }
                            server.overworld().setBlock(beacon,
                                    net.minecraft.world.level.block.Blocks.BEACON.defaultBlockState(), 3);
                            LoggerFactory.getLogger("veyra-graphics-smoke").info("Placed a complete beacon pyramid at {}", beacon);
                        } else if (itemFixture) {
                            var player = server.getPlayerList().getPlayer(client.player.getUUID());
                            if (player == null) throw new IllegalStateException("Could not find the smoke-test player");
                            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                                    new net.minecraft.world.item.ItemStack(glintVertexFixture || glintMrtFixture
                                            ? net.minecraft.world.item.Items.ENCHANTED_BOOK
                                            : net.minecraft.world.item.Items.DIAMOND));
                            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
                            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                    "Equipped item fixture items: mainhand={}, selectedSlot={}, selected={}, offhand={}",
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()),
                                    player.getInventory().getSelectedSlot(),
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                                            player.getInventory().getSelectedItem().getItem()),
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()));
                        } else if (blockVertexFixture) {
                            var chest = net.minecraft.core.BlockPos.containing(playerX - 4.0, playerY, playerZ + 4.0);
                            server.overworld().setBlock(chest,
                                    net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
                            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                    "Placed block-vertex fixture chest at {}", chest);
                        } else {
                            var cow = net.minecraft.world.entity.EntityType.COW.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                            if (cow == null) throw new IllegalStateException("Could not create material fixture cow");
                            cow.setPos(playerX - 4.0, playerY, playerZ + 4.0);
                            cow.setNoAi(true);
                            java.util.Objects.requireNonNull(cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)).setBaseValue(2.0);
                            server.overworld().addFreshEntity(cow);
                            if (basicVertexFixture) {
                                var fence = net.minecraft.core.BlockPos.containing(playerX - 1.0, playerY - 1.0, playerZ + 1.0);
                                server.overworld().setBlock(fence, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState(), 3);
                                var knot = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(
                                        server.overworld(), fence);
                                ((net.minecraft.world.entity.Leashable) cow).setLeashedTo(knot, true);
                            }
                        }
                    } catch (Throwable failure) { entityFixtureFailure = failure; }
                });
            }
            if (entityFixtureFailure != null) throw new IllegalStateException("Entity material fixture setup failed", entityFixtureFailure);
            if (thirdPartyShaderpackFixture && !thirdPartyScenePrepared && worldTicks >= 20
                    && client.getSingleplayerServer() != null) {
                thirdPartyScenePrepared = true;
                var server = client.getSingleplayerServer();
                client.getConnection().sendCommand("tp @s 0 72 0");
                server.execute(() -> {
                    var world = server.overworld();
                    world.setDayTime(1000L);
                    for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                        var block = Math.floorMod(x * 3 + z, 7) == 0
                                ? net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()
                                : Math.floorMod(x + z * 2, 9) == 0
                                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                        world.setBlock(new net.minecraft.core.BlockPos(x, 70, z), block, 3);
                    }
                    var materials = new net.minecraft.world.level.block.state.BlockState[]{
                            net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.MOSS_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.LAPIS_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.RED_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.BLUE_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.YELLOW_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.LIME_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.PURPLE_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.CYAN_WOOL.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.PRISMARINE.defaultBlockState(),
                            net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState()
                    };
                    for (int row = 0; row < 7; row++) for (int column = 0; column < 17; column++) {
                        world.setBlock(new net.minecraft.core.BlockPos(-8 + column, 71 + row, 8),
                                materials[(row + column) % materials.length], 3);
                    }
                    world.setBlock(new net.minecraft.core.BlockPos(-9, 71, 8),
                            net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
                    world.setBlock(new net.minecraft.core.BlockPos(9, 71, 8),
                            net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
                });
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Prepared the real shaderpack fixture with daylight, a mixed block field and a lit material wall.");
            }
            if (damagedBlockVertexFixture && damagedBlockPosition != null
                    && client.level.getBlockState(damagedBlockPosition).isSolid()) {
                client.levelRenderer.destroyBlockProgress(-9876, damagedBlockPosition, 6);
            }
            client.options.hideGui = !itemFixture;
            client.player.setXRot(thirdPartyShaderpackFixture ? -2.0F
                    : entityFixture || beaconBeamFixture ? 15.0F
                    : damagedBlockVertexFixture ? 20.0F
                    : cloudFixture || skyBasicVertexFixture || skyTexturedStageFixture ? -60.0F : 35.0F);
            client.player.setYRot(thirdPartyShaderpackFixture ? 0.0F : 45.0F);
            if (vanillaBaseline && worldTicks >= 240) {
                if (VeyraRenderer.isRenderingEnabled() || VeyraRenderer.selectedPack().isPresent())
                    throw new IllegalStateException("Vanilla baseline unexpectedly selected a shaderpack");
                pending = true; requested = true; captureActive = false;
            } else if (!vanillaBaseline && stage == 0 && worldTicks >= 160) {
                if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty())
                    throw new IllegalStateException("Shader pipeline failed: " + VeyraRenderer.getLastError());
                if (cloudSceneColorFixture && !VeyraRenderer.hasCapturedCloudSceneColor())
                    throw new IllegalStateException("The cloud scene-color sampler was not captured before drawing clouds");
                pending = true;
                requested = true; captureActive = true;
            } else if (!vanillaBaseline && (stage == 1 || stage == 2) && worldTicks >= baselineAt) {
                pending = true;
                requested = true; captureActive = (skyTexturedStageFixture || shadowColorMrtFixture) && stage == 2;
            } else if (shadowColorMrtLifecycleFixture && stage == 3 && worldTicks >= baselineAt) {
                pending = true;
                requested = true;
                captureActive = true;
            } else if (shadowColorMrtLifecycleFixture && stage == 4
                    && client.level.dimension().equals(net.minecraft.world.level.Level.NETHER)
                    && worldTicks >= baselineAt) {
                var pack = VeyraRenderer.selectedPack().orElse(null);
                if (!VeyraRenderer.isRenderingEnabled() || pack == null
                        || pack.configuration().dimension() != -1 || !VeyraRenderer.getLastError().isEmpty()) {
                    throw new IllegalStateException("Shader pack did not survive the Nether transition: "
                            + VeyraRenderer.getLastError());
                }
                client.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 -55 0");
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Returning lifecycle fixture player from Nether to Overworld");
                stage = 5;
                baselineAt = worldTicks + 100;
            } else if (shadowColorMrtLifecycleFixture && stage == 5
                    && client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    && worldTicks >= baselineAt) {
                var pack = VeyraRenderer.selectedPack().orElse(null);
                if (!VeyraRenderer.isRenderingEnabled() || pack == null
                        || pack.configuration().dimension() != 0 || !VeyraRenderer.getLastError().isEmpty()) {
                    throw new IllegalStateException("Shader pack did not recover after returning to the Overworld: "
                            + VeyraRenderer.getLastError());
                }
                stage = 6;
                pending = true;
                requested = true;
                captureActive = true;
            }
        } catch (Throwable failure) { finish(client, failure, -1); }
    }

    /** Record after drawing, before present; consume mapped bytes only after this frame's fence. */
    public static void renderedFrame() {
        if (!requested || finished) return;
        requested = false;
        Minecraft client = Minecraft.getInstance();
        Buffer readback = null;
        Buffer glintMrtReadback = null;
        Buffer basicMrtReadback = null;
        ComputeImageProbe computeProbe = null;
        try {
            Renderer renderer = Renderer.getInstance();
            renderer.endRenderPass();
            if (captureActive && Boolean.getBoolean("veyra.graphicsSmoke.computeProbe")) {
                computeProbe = ComputeImageProbe.record();
            }
            var source = renderer.getMainPass().getMainFramebuffer().getColorAttachment();
            if ((source.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0)
                throw new IllegalStateException("Surface image does not support frame readback");
            // These fixtures validate their diagnostic signal in the presented colortex0 image.
            // The entity feedback fixture also writes colortex4, but does not use it as its signal.
            var auxiliary = VeyraRenderer.isRenderingEnabled() && !cloudFixture && !geometryViewSpaceFixture
                    && !geometryMidTexCoordFixture && !terrainMaterialFixture && !waterMaterialFixture
                    && !terrainVertexExecutionFixture && !terrainVertexVaryingsFixture
                    && !entityColorFeedbackFixture && !currentRenderedItemFixture && !glintVertexFixture
                    && !glintMrtFixture && !basicVertexFixture && !skyBasicVertexFixture && !beaconBeamFixture
                    && !entityCustomImageFixture && !compositeCustomImageFixture
                    && !skyTexturedStageFixture && !blockVertexFixture && !damagedBlockVertexFixture && !handVertexFixture
                    && !weatherVertexFixture && !particleVertexFixture && !texturedDefaultAttributesFixture
                    && !storageBufferFixture
                    ? VeyraRenderer.geometryColorAttachment(1) : null;
            for (var custom : dev.veyra.shader.CustomUniforms.active().uniforms()) {
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Custom uniform {} ({}) = {} / {}", custom.name(), custom.type(),
                        dev.veyra.shader.CustomUniforms.active().floatValue(custom.name()), dev.veyra.shader.CustomUniforms.active().intValue(custom.name()));
            }
            LoggerFactory.getLogger("veyra-graphics-smoke").info("Frame evidence: active={} main={} auxiliary={}",
                    captureActive, Long.toHexString(source.getId()), auxiliary == null ? "disabled" : Long.toHexString(auxiliary.getId()));
            int width = source.width, height = source.height, format = source.format;
            if (format != VK_FORMAT_B8G8R8A8_UNORM && format != VK_FORMAT_B8G8R8A8_SRGB
                    && format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_R8G8B8A8_SRGB)
                throw new IllegalStateException("Unsupported readback format " + format);
            readback = new Buffer("Veyra graphics evidence", VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
            readback.createBuffer((long) width * height * 4);
            var glintMrtSource = glintMrtFixture && captureActive
                    ? VeyraRenderer.geometryColorAttachment(4) : null;
            if (glintMrtSource != null) {
                if (glintMrtSource.width != width || glintMrtSource.height != height
                        || glintMrtSource.format != VK_FORMAT_R8G8B8A8_UNORM
                        || (glintMrtSource.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
                    throw new IllegalStateException("glint MRT colortex4 is not a readable RGBA8 transfer source");
                }
                glintMrtReadback = new Buffer("Veyra glint MRT evidence", VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
                glintMrtReadback.createBuffer((long) width * height * 4);
            }
            var basicMrtSource = (basicVertexFixture && !basicCustomImageFixture || beaconBeamFixture) && captureActive
                    ? VeyraRenderer.geometryColorAttachment(6) : null;
            if (basicMrtSource != null) {
                if (basicMrtSource.width != width || basicMrtSource.height != height
                        || basicMrtSource.format != VK_FORMAT_R8G8B8A8_UNORM
                        || (basicMrtSource.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0) {
                    throw new IllegalStateException("geometry MRT colortex6 is not a readable RGBA8 transfer source");
                }
                basicMrtReadback = new Buffer("Veyra basic MRT evidence", VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
                basicMrtReadback.createBuffer((long) width * height * 4);
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var commands = Renderer.getCommandBuffer();
                int layout = source.getCurrentLayout();
                source.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                var region = VkBufferImageCopy.calloc(1, stack);
                region.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                region.imageExtent().set(width, height, 1);
                vkCmdCopyImageToBuffer(commands, source.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback.getId(), region);
                var barrier = VkMemoryBarrier.calloc(1, stack).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_HOST_READ_BIT);
                vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                        0, barrier, null, null);
                source.transitionImageLayout(stack, commands, layout);
                if (glintMrtSource != null) {
                    int mrtLayout = glintMrtSource.getCurrentLayout();
                    glintMrtSource.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                    region.bufferOffset(0);
                    vkCmdCopyImageToBuffer(commands, glintMrtSource.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            glintMrtReadback.getId(), region);
                    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                            0, barrier, null, null);
                    glintMrtSource.transitionImageLayout(stack, commands, mrtLayout);
                }
                if (basicMrtSource != null) {
                    int mrtLayout = basicMrtSource.getCurrentLayout();
                    basicMrtSource.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                    region.bufferOffset(0);
                    vkCmdCopyImageToBuffer(commands, basicMrtSource.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            basicMrtReadback.getId(), region);
                    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                            0, barrier, null, null);
                    basicMrtSource.transitionImageLayout(stack, commands, mrtLayout);
                }
            }
            Buffer completed = readback;
            Buffer completedGlintMrt = glintMrtReadback;
            Buffer completedBasicMrt = basicMrtReadback;
            ComputeImageProbe completedComputeProbe = computeProbe;
            boolean active = captureActive;
            MemoryManager.getInstance().addFrameOp(() -> {
                try {
                    if (completedComputeProbe != null) completedComputeProbe.verify();
                    var bytes = MemoryUtil.memByteBuffer(completed.getDataPtr(), width * height * 4);
                    NativeImage image = new NativeImage(width, height, false);
                    boolean bgra = format == VK_FORMAT_B8G8R8A8_UNORM || format == VK_FORMAT_B8G8R8A8_SRGB;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int offset = (y * width + x) * 4;
                        int first = bytes.get(offset) & 255, green = bytes.get(offset + 1) & 255;
                        int third = bytes.get(offset + 2) & 255, alpha = bytes.get(offset + 3) & 255;
                        image.setPixel(x, y, (alpha << 24) | ((bgra ? third : first) << 16)
                                | (green << 8) | (bgra ? first : third));
                    }
                    NativeImage mrtImage = null;
                    if (completedGlintMrt != null) {
                        var mrtBytes = MemoryUtil.memByteBuffer(completedGlintMrt.getDataPtr(), width * height * 4);
                        mrtImage = new NativeImage(width, height, false);
                        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                            int offset = (y * width + x) * 4;
                            mrtImage.setPixel(x, y, (mrtBytes.get(offset + 3) & 255) << 24
                                    | (mrtBytes.get(offset) & 255) << 16
                                    | (mrtBytes.get(offset + 1) & 255) << 8
                                    | (mrtBytes.get(offset + 2) & 255));
                        }
                    }
                    NativeImage basicMrtImage = null;
                    if (completedBasicMrt != null) {
                        var mrtBytes = MemoryUtil.memByteBuffer(completedBasicMrt.getDataPtr(), width * height * 4);
                        basicMrtImage = new NativeImage(width, height, false);
                        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                            int offset = (y * width + x) * 4;
                            basicMrtImage.setPixel(x, y, (mrtBytes.get(offset + 3) & 255) << 24
                                    | (mrtBytes.get(offset) & 255) << 16
                                    | (mrtBytes.get(offset + 1) & 255) << 8
                                    | (mrtBytes.get(offset + 2) & 255));
                        }
                    }
                    capture(client, image, active, mrtImage, basicMrtImage);
                } catch (Throwable failure) { finish(client, failure, -1); }
                finally {
                    if (completedComputeProbe != null) completedComputeProbe.close();
                    completed.scheduleFree();
                    if (completedGlintMrt != null) completedGlintMrt.scheduleFree();
                    if (completedBasicMrt != null) completedBasicMrt.scheduleFree();
                }
            });
        } catch (Throwable failure) {
            if (computeProbe != null) computeProbe.close();
            if (readback != null) readback.scheduleFree();
            if (glintMrtReadback != null) glintMrtReadback.scheduleFree();
            if (basicMrtReadback != null) basicMrtReadback.scheduleFree();
            finish(client, failure, -1);
        }
    }

    private static void capture(Minecraft client, NativeImage image, boolean active,
                                NativeImage glintMrtImage, NativeImage basicMrtImage) {
        int frameWidth = image.getWidth();
        int frameHeight = image.getHeight();
        int colored = 0;
        int opposite = 0;
        int skyMoonGreen = 0;
        int renderedItemGreen = 0;
        int glintMrtPixels = 0;
        int basicMrtPixels = 0;
        int nonBlackPixels = 0;
        int colorBuckets = 0;
        boolean[] colors = new boolean[512];
        try (image; glintMrtImage; basicMrtImage) {
            Files.createDirectories(output);
                    String imageName = thirdPartyShaderpackFixture ? "third-party-pack-active.png"
                    : storageBufferFixture ? active ? "storage-buffer-active.png" : "storage-buffer-disabled.png"
                    : shadowColorMrtFixture && stage == 2 ? "shadow-mrt-reenabled.png"
                    : shadowColorMrtLifecycleFixture && stage == 3 ? "shadow-mrt-resized.png"
                    : shadowColorMrtLifecycleFixture && stage == 6 ? "shadow-mrt-dimension-return.png"
                    : skyTexturedStageFixture
                    ? active && stage == 2 ? "skytextured-moon-active.png"
                            : active ? "skytextured-sun-active.png" : "skytextured-disabled.png"
                    : active ? "mrt-active.png" : "shaders-disabled.png";
            image.writeToFile(output.resolve(imageName));
            if (glintMrtImage != null) {
                glintMrtImage.writeToFile(output.resolve("glint-mrt-colortex4.png"));
                for (int y = 0; y < glintMrtImage.getHeight(); y++) for (int x = 0; x < glintMrtImage.getWidth(); x++) {
                    int pixel = glintMrtImage.getPixel(x, y);
                    int red = (pixel >>> 16) & 255, green = (pixel >>> 8) & 255, blue = pixel & 255;
                    if (red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) glintMrtPixels++;
                }
            }
            if (basicMrtImage != null) {
                basicMrtImage.writeToFile(output.resolve(beaconBeamFixture
                        ? "beaconbeam-vertex-mrt-colortex6.png" : "basic-vertex-mrt-colortex6.png"));
                for (int y = 0; y < basicMrtImage.getHeight(); y++) for (int x = 0; x < basicMrtImage.getWidth(); x++) {
                    int pixel = basicMrtImage.getPixel(x, y);
                    int red = (pixel >>> 16) & 255, green = (pixel >>> 8) & 255, blue = pixel & 255;
                    if (red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) basicMrtPixels++;
                }
            }
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixel(x, y);
                int red = (pixel >>> 16) & 255, green = (pixel >>> 8) & 255, blue = pixel & 255;
                if (Math.max(red, Math.max(green, blue)) > 12) {
                    nonBlackPixels++;
                    int bucket = ((red >>> 5) << 6) | ((green >>> 5) << 3) | (blue >>> 5);
                    if (!colors[bucket]) { colors[bucket] = true; colorBuckets++; }
                }
                if (red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) colored++;
                if (skyTexturedStageFixture && green > 220 && red < 24 && blue < 24) skyMoonGreen++;
                if (geometryMidTexCoordFixture && green > 90 && red < green * 0.6 && blue < green * 0.6) opposite++;
                if ((currentRenderedItemFixture || glintVertexFixture || glintMrtFixture || basicVertexFixture
                        || beaconBeamFixture || entityCustomImageFixture)
                        && green > 220 && red < 24 && blue < 24) renderedItemGreen++;
            }
            int count = colored;
            int oppositeCount = opposite;
            int skyMoonGreenCount = skyMoonGreen;
            int renderedItemGreenCount = renderedItemGreen;
            int glintMrtPixelCount = glintMrtPixels;
            int basicMrtPixelCount = basicMrtPixels;
            int nonBlackPixelCount = nonBlackPixels;
            int colorBucketCount = colorBuckets;
            completion = () -> {
                if (thirdPartyShaderpackFixture) {
                    thirdPartyNonBlackPixels = nonBlackPixelCount;
                    thirdPartyColorBuckets = colorBucketCount;
                    Throwable failure = !thirdPartyProfileApplied || !VeyraRenderer.isRenderingEnabled()
                            || !VeyraRenderer.getLastError().isEmpty()
                            ? new IllegalStateException("The real shaderpack/profile was no longer active: "
                                    + VeyraRenderer.getLastError())
                            : nonBlackPixelCount < 10000 || colorBucketCount < 24
                            ? new IllegalStateException("The real shaderpack frame was empty or nearly uniform: nonBlack="
                                    + nonBlackPixelCount + ", colorBuckets=" + colorBucketCount)
                            : null;
                    activePixels = count;
                    finish(client, failure, -1);
                } else if (vanillaBaseline) {
                    finish(client, count >= 2000 ? new IllegalStateException("Unexpected shader signal in vanilla baseline: " + count) : null, count);
                } else if (active) {
                    if (storageBufferFixture && count < 2000) {
                        finish(client, new IllegalStateException("The shadow storage-buffer write/read signal is absent: "
                                + count + " magenta pixels"), -1);
                        return;
                    }
                    if (shadowColorMrtFixture && stage == 2) {
                        reenabledShadowMrtPixels = count;
                        if (shadowColorMrtLifecycleFixture) {
                            if (count < 2000) {
                                finish(client, new IllegalStateException("Shadow MRT did not recover before lifecycle checks: "
                                        + count + " active pixels"), disabledPixels);
                                return;
                            }
                            lifecycleInitialWidth = frameWidth;
                            lifecycleInitialHeight = frameHeight;
                            int resizedWidth = frameWidth == 800 ? 960 : 800;
                            int resizedHeight = resizedWidth * 9 / 16;
                            client.getWindow().setWindowed(resizedWidth, resizedHeight);
                            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                    "Resizing lifecycle fixture from {}x{} to {}x{}", frameWidth, frameHeight, resizedWidth, resizedHeight);
                            stage = 3;
                            baselineAt = worldTicks + 100;
                            pending = false;
                            return;
                        }
                        finish(client, count < 2000
                                ? new IllegalStateException("Shadow MRT did not recover after shader-pack re-selection: "
                                        + reenabledShadowMrtPixels + " active pixels") : null,
                                disabledPixels);
                        return;
                    }
                    if (shadowColorMrtLifecycleFixture && stage == 3) {
                        var colortex = VeyraRenderer.geometryColorAttachment(1);
                        if (frameWidth == lifecycleInitialWidth && frameHeight == lifecycleInitialHeight) {
                            finish(client, new IllegalStateException("Window resize did not change the presented framebuffer: "
                                    + frameWidth + "x" + frameHeight), count);
                            return;
                        }
                        if (colortex.width != frameWidth || colortex.height != frameHeight) {
                            finish(client, new IllegalStateException("colortex1 did not follow the resized framebuffer: surface="
                                    + frameWidth + "x" + frameHeight + ", colortex1=" + colortex.width + "x" + colortex.height), count);
                            return;
                        }
                        client.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0 80 0");
                        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                "Resize lifecycle passed at {}x{}; moving fixture player to Nether", frameWidth, frameHeight);
                        stage = 4;
                        baselineAt = worldTicks + 100;
                        pending = false;
                        return;
                    }
                    if (shadowColorMrtLifecycleFixture && stage == 6) {
                        if (count < 2000) {
                            finish(client, new IllegalStateException("Shadow MRT did not recover after dimension round trip: "
                                    + count + " active pixels"), count);
                            return;
                        }
                        reenabledShadowMrtPixels = count;
                        activePixels = count;
                        finish(client, null, disabledPixels);
                        return;
                    }
                    if (skyTexturedStageFixture && stage == 0) {
                        activeSkySunPixels = count;
                        activePixels = count;
                        if (activeSkySunPixels < 100) {
                            finish(client, new IllegalStateException("The celestial SUN render-stage signal was absent: "
                                    + activeSkySunPixels), activeSkySunPixels);
                            return;
                        }
                        var server = client.getSingleplayerServer();
                        if (server == null) {
                            finish(client, new IllegalStateException("The sky render-stage fixture has no singleplayer server"), -1);
                            return;
                        }
                        server.execute(() -> server.overworld().setDayTime(18000L));
                        stage = 2;
                        baselineAt = worldTicks + 100;
                        pending = false;
                        return;
                    }
                    if (skyTexturedStageFixture && stage == 2) {
                        activeSkyMoonPixels = skyMoonGreenCount;
                        if (activeSkyMoonPixels < 100) {
                            finish(client, new IllegalStateException("The celestial MOON render-stage signal was absent: "
                                    + activeSkyMoonPixels), activeSkyMoonPixels);
                            return;
                        }
                        VeyraRenderer.disable();
                        baselineAt = worldTicks + 80;
                        stage = 1;
                        pending = false;
                        return;
                    }
                    activePixels = count;
                    activeOppositeMidTexCoordPixels = oppositeCount;
                    activeCurrentRenderedItemGreenPixels = renderedItemGreenCount;
                    activeGlintGreenPixels = renderedItemGreenCount;
                    activeGlintMrtPixels = glintMrtPixelCount;
                    activeBasicGreenPixels = renderedItemGreenCount;
                    activeBasicMrtPixels = basicMrtPixelCount;
                    if (entityCustomImageFixture) activeEntityCustomImageGreenPixels = renderedItemGreenCount;
                    if (beaconBeamFixture) {
                        activeBeaconBeamGreenPixels = renderedItemGreenCount;
                        activeBeaconBeamMrtPixels = basicMrtPixelCount;
                    }
                    if (glintMrtFixture) {
                        finish(client, activeGlintGreenPixels < 100 || activeGlintMrtPixels < 100
                                ? new IllegalStateException("The glint MRT outputs were absent: scene green="
                                + activeGlintGreenPixels + ", colortex4 magenta=" + activeGlintMrtPixels) : null,
                                activeGlintGreenPixels);
                        return;
                    }
                    if (shadowColorMrtFixture && stage == 0) {
                        shadowMrtPack = VeyraRenderer.selectedPack().orElseThrow(
                                () -> new IllegalStateException("The shadow MRT fixture lost its selected shader pack."));
                    }
                    VeyraRenderer.disable();
                    baselineAt = worldTicks + 80;
                    stage = 1;
                    pending = false;
                } else {
                    disabledCurrentRenderedItemGreenPixels = renderedItemGreenCount;
                    disabledGlintGreenPixels = renderedItemGreenCount;
                    disabledBasicGreenPixels = renderedItemGreenCount;
                    disabledBasicMrtPixels = basicMrtPixelCount;
                    if (entityCustomImageFixture) disabledEntityCustomImageGreenPixels = renderedItemGreenCount;
                    if (beaconBeamFixture) {
                        disabledBeaconBeamGreenPixels = renderedItemGreenCount;
                        disabledBeaconBeamMrtPixels = basicMrtPixelCount;
                    }
                    int minPixels = currentRenderedItemFixture || blockVertexFixture || entityCustomImageFixture
                            || damagedBlockVertexFixture || handVertexFixture || weatherVertexFixture || particleVertexFixture
                            || texturedDefaultAttributesFixture ? 100 : 2000;
                    Throwable failure;
                    if (skyTexturedStageFixture) {
                        disabledSkySunPixels = count;
                        disabledSkyMoonPixels = skyMoonGreenCount;
                        failure = disabledSkySunPixels * 4 >= activeSkySunPixels
                                || disabledSkyMoonPixels * 4 >= activeSkyMoonPixels
                                ? new IllegalStateException("The sky-textured stage signals were absent or unchanged after disabling: sun active/disabled="
                                        + activeSkySunPixels + "/" + disabledSkySunPixels
                                        + ", moon active/disabled=" + activeSkyMoonPixels + "/" + disabledSkyMoonPixels)
                                : null;
                    } else if (beaconBeamFixture) {
                        failure = activeBeaconBeamGreenPixels < 100 || activeBeaconBeamMrtPixels < 100
                                || disabledBeaconBeamGreenPixels * 4 >= activeBeaconBeamGreenPixels
                                || disabledBeaconBeamMrtPixels * 4 >= activeBeaconBeamMrtPixels
                                ? new IllegalStateException("The beacon vertex/MRT signal was absent or unchanged: scene active/disabled="
                                + activeBeaconBeamGreenPixels + "/" + disabledBeaconBeamGreenPixels
                                + ", colortex6 active/disabled=" + activeBeaconBeamMrtPixels + "/"
                                + disabledBeaconBeamMrtPixels) : null;
                    } else if (entityCustomImageFixture) {
                        failure = activeEntityCustomImageGreenPixels < 100
                                || disabledEntityCustomImageGreenPixels * 4 >= activeEntityCustomImageGreenPixels
                                ? new IllegalStateException("The entity custom-image signal was absent or unchanged: scene green active/disabled="
                                        + activeEntityCustomImageGreenPixels + "/" + disabledEntityCustomImageGreenPixels) : null;
                    } else if (storageBufferFixture) {
                        failure = activePixels < 2000 || count * 4 >= activePixels
                                ? new IllegalStateException("The shadow storage-buffer signal was absent or unchanged: active="
                                + activePixels + ", disabled=" + count) : null;
                    } else if (basicCustomImageFixture) {
                        failure = activeBasicGreenPixels < 100
                                || disabledBasicGreenPixels * 4 >= activeBasicGreenPixels
                                ? new IllegalStateException("The basic custom-image signal was absent or unchanged: scene green active/disabled="
                                        + activeBasicGreenPixels + "/" + disabledBasicGreenPixels) : null;
                    } else if (basicVertexFixture) {
                        failure = activeBasicGreenPixels < 100 || activeBasicMrtPixels < 100
                                || disabledBasicGreenPixels * 4 >= activeBasicGreenPixels
                                || disabledBasicMrtPixels * 4 >= activeBasicMrtPixels
                                ? new IllegalStateException("The basic vertex/MRT signal was absent or unchanged: scene active/disabled="
                                + activeBasicGreenPixels + "/" + disabledBasicGreenPixels + ", colortex6 active/disabled="
                                + activeBasicMrtPixels + "/" + disabledBasicMrtPixels) : null;
                    } else if (glintVertexFixture) {
                        failure = activeGlintGreenPixels < 100 || disabledGlintGreenPixels * 4 >= activeGlintGreenPixels
                                ? new IllegalStateException("The armor-glint vertex-stage signal was absent or unchanged: active="
                                + activeGlintGreenPixels + ", disabled=" + disabledGlintGreenPixels) : null;
                    } else if (terrainVertexExecutionFixture) {
                        failure = activePixels >= minPixels || count >= minPixels
                                ? new IllegalStateException("The terrain vertex main did not clip its magenta geometry: active="
                                + activePixels + ", disabled=" + count) : null;
                    } else if (skyBasicVertexFixture) {
                        failure = activePixels < 2000 || count * 4 >= activePixels
                                ? new IllegalStateException("The sky-basic vertex-stage signal was absent or unchanged: active="
                                + activePixels + ", disabled=" + count) : null;
                    } else {
                        failure = activePixels < minPixels || count * 4 >= activePixels
                                || geometryMidTexCoordFixture && activeOppositeMidTexCoordPixels < 2000
                                || currentRenderedItemFixture && (activeCurrentRenderedItemGreenPixels < minPixels
                                || renderedItemGreenCount * 4 >= activeCurrentRenderedItemGreenPixels)
                                ? new IllegalStateException("Auxiliary MRT, midpoint, or currentRenderedItemId signal absent or unchanged: active="
                                + activePixels + ", opposite=" + activeOppositeMidTexCoordPixels + ", disabled=" + count
                                + ", itemGreen=" + activeCurrentRenderedItemGreenPixels
                                + ", disabledItemGreen=" + renderedItemGreenCount) : null;
                    }
                    if (shadowColorMrtFixture && failure == null) {
                        disabledPixels = count;
                        if (shadowMrtPack == null) {
                            finish(client, new IllegalStateException("The shadow MRT fixture has no shader pack to re-select."), count);
                            return;
                        }
                        String selection = VeyraRenderer.select(shadowMrtPack);
                        if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty()) {
                            finish(client, new IllegalStateException("Could not re-select the shadow MRT fixture pack: "
                                    + selection + " (" + VeyraRenderer.getLastError() + ")"), count);
                            return;
                        }
                        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                "Re-selected shader pack after the disabled shadow MRT resource cycle.");
                        stage = 2;
                        baselineAt = worldTicks + 80;
                        pending = false;
                        return;
                    }
                    finish(client, failure, count);
                }
            };
        } catch (Throwable failure) { completion = () -> finish(client, failure, -1); }
    }

    private static void finish(Minecraft client, Throwable failure, int baseline) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(output);
            String error = failure == null ? "" : failure.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            String renderer = physicalDeviceName().replace("\\", "\\\\").replace("\"", "\\\"");
            String profile = thirdPartyProfile.replace("\\", "\\\\").replace("\"", "\\\"");
            String escapedSkipReason = skipReason.replace("\\", "\\\\").replace("\"", "\\\"");
            String status = failure != null ? "failed" : skipReason.isEmpty() ? "passed" : "skipped";
            Files.writeString(output.resolve("result.json"), "{\"status\":\"" + status
                    + "\",\"renderer\":\"" + renderer + "\",\"activeMagentaPixels\":" + activePixels
                    + ",\"vertexPipelineStoresAndAtomicsEnabled\":"
                    + VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled
                    + ",\"shaderStorageImageExtendedFormatsEnabled\":"
                    + VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled
                    + ",\"skipReason\":\"" + escapedSkipReason + "\""
                    + ",\"thirdPartyProfile\":\"" + profile + "\",\"thirdPartyNonBlackPixels\":" + thirdPartyNonBlackPixels
                    + ",\"thirdPartyColorBuckets\":" + thirdPartyColorBuckets
                    + ",\"shadowMrtReenabledPixels\":" + reenabledShadowMrtPixels
                    + ",\"lifecycleInitialWidth\":" + lifecycleInitialWidth
                    + ",\"lifecycleInitialHeight\":" + lifecycleInitialHeight
                    + ",\"activeOppositeMidTexCoordPixels\":" + activeOppositeMidTexCoordPixels
                    + ",\"activeCurrentRenderedItemGreenPixels\":" + activeCurrentRenderedItemGreenPixels
                    + ",\"disabledMagentaPixels\":" + baseline
                    + ",\"disabledCurrentRenderedItemGreenPixels\":" + disabledCurrentRenderedItemGreenPixels
                    + ",\"activeGlintGreenPixels\":" + activeGlintGreenPixels
                    + ",\"disabledGlintGreenPixels\":" + disabledGlintGreenPixels
                    + ",\"activeGlintMrtPixels\":" + activeGlintMrtPixels
                    + ",\"activeBasicGreenPixels\":" + activeBasicGreenPixels
                    + ",\"disabledBasicGreenPixels\":" + disabledBasicGreenPixels
                    + ",\"activeEntityCustomImageGreenPixels\":" + activeEntityCustomImageGreenPixels
                    + ",\"disabledEntityCustomImageGreenPixels\":" + disabledEntityCustomImageGreenPixels
                    + ",\"activeBasicMrtPixels\":" + activeBasicMrtPixels
                    + ",\"disabledBasicMrtPixels\":" + disabledBasicMrtPixels
                    + ",\"activeBeaconBeamGreenPixels\":" + activeBeaconBeamGreenPixels
                    + ",\"disabledBeaconBeamGreenPixels\":" + disabledBeaconBeamGreenPixels
                    + ",\"activeBeaconBeamMrtPixels\":" + activeBeaconBeamMrtPixels
                    + ",\"disabledBeaconBeamMrtPixels\":" + disabledBeaconBeamMrtPixels
                    + ",\"activeSkyTexturedSunPixels\":" + activeSkySunPixels
                    + ",\"activeSkyTexturedMoonPixels\":" + activeSkyMoonPixels
                    + ",\"disabledSkyTexturedSunPixels\":" + disabledSkySunPixels
                    + ",\"disabledSkyTexturedMoonPixels\":" + disabledSkyMoonPixels
                    + ",\"error\":\"" + error + "\"}\n");
        } catch (Exception writeFailure) { LoggerFactory.getLogger("veyra-graphics-smoke").error("Could not save graphics result", writeFailure); }
        if (failure != null) LoggerFactory.getLogger("veyra-graphics-smoke").error("Graphics integration check failed", failure);
        client.stop();
    }

    private static String physicalDeviceName() {
        if (DeviceManager.physicalDevice == null) return "unknown Vulkan device";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(DeviceManager.physicalDevice, properties);
            return properties.deviceNameString();
        } catch (Throwable failure) {
            LoggerFactory.getLogger("veyra-graphics-smoke").warn("Could not read Vulkan device name", failure);
            return "unknown Vulkan device";
        }
    }
}
