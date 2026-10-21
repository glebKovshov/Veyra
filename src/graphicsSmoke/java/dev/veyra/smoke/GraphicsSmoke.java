package dev.veyra.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.VeyraRenderer;
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
    private static int ticks, worldTicks, stage, activePixels, baselineAt;
    private static boolean pending, finished, requested, captureActive;
    private static Runnable completion;
    private static final Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
    private static boolean entityFixtureStarted, vanillaBaseline, cloudFixture, cloudSceneColorFixture,
            geometryViewSpaceFixture,
            geometryMidTexCoordFixture, terrainMaterialFixture, terrainVertexExecutionFixture,
            terrainVertexVaryingsFixture, waterMaterialFixture,
            entityMrtFixture, entityColorFeedbackFixture, currentRenderedItemFixture, glintVertexFixture,
            glintMrtFixture, basicVertexFixture, skyBasicVertexFixture, skyTexturedStageFixture,
            beaconBeamFixture, texturedDefaultAttributesFixture, blockVertexFixture,
            damagedBlockVertexFixture, handVertexFixture, weatherVertexFixture, particleVertexFixture;
    private static volatile net.minecraft.core.BlockPos damagedBlockPosition;
    private static int activeOppositeMidTexCoordPixels, activeCurrentRenderedItemGreenPixels,
            disabledCurrentRenderedItemGreenPixels, activeGlintGreenPixels, disabledGlintGreenPixels,
            activeGlintMrtPixels, activeBasicGreenPixels, disabledBasicGreenPixels, activeBasicMrtPixels,
            disabledBasicMrtPixels, activeBeaconBeamGreenPixels, disabledBeaconBeamGreenPixels,
            activeBeaconBeamMrtPixels, disabledBeaconBeamMrtPixels,
            activeSkySunPixels, activeSkyMoonPixels, disabledSkySunPixels, disabledSkyMoonPixels;
    private static volatile Throwable entityFixtureFailure;
    private GraphicsSmoke() { }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || finished) return;
        try {
            if (completion != null) {
                Runnable ready = completion; completion = null; ready.run();
                if (finished) return;
            }
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the validation world/readback; screen=" + client.screen);
            if (client.level == null || client.player == null || client.screen != null || pending) return;
            worldTicks++;
            String fixture = Files.readString(client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt"));
            vanillaBaseline = fixture.equals("vanilla-baseline");
            cloudSceneColorFixture = fixture.equals("cloud-colortex0");
            cloudFixture = cloudSceneColorFixture || fixture.equals("cloud-texture-inputs")
                    || fixture.equals("cloud-model-view");
            geometryViewSpaceFixture = fixture.equals("geometry-view-space");
            geometryMidTexCoordFixture = fixture.equals("geometry-mid-texcoord");
            terrainMaterialFixture = fixture.equals("terrain-block-id");
            terrainVertexExecutionFixture = fixture.equals("terrain-vertex-execution");
            terrainVertexVaryingsFixture = fixture.equals("terrain-vertex-varyings");
            waterMaterialFixture = fixture.equals("water-block-id");
            entityMrtFixture = fixture.equals("entity-mrt");
            entityColorFeedbackFixture = fixture.equals("entity-colortex-feedback");
            currentRenderedItemFixture = fixture.equals("current-rendered-item-id");
            glintVertexFixture = fixture.equals("glint-vertex-execution");
            glintMrtFixture = fixture.equals("glint-mrt-outputs");
            basicVertexFixture = fixture.equals("basic-vertex-execution");
            skyBasicVertexFixture = fixture.equals("skybasic-vertex-execution");
            skyTexturedStageFixture = fixture.equals("skytextured-stage-variants");
            beaconBeamFixture = fixture.equals("beaconbeam-vertex-execution");
            texturedDefaultAttributesFixture = fixture.equals("textured-default-vertex-attributes");
            weatherVertexFixture = fixture.equals("weather-vertex-execution");
            particleVertexFixture = fixture.equals("particles-translucent-vertex-execution");
            blockVertexFixture = fixture.equals("block-vertex-execution");
            damagedBlockVertexFixture = fixture.equals("damagedblock-vertex-execution");
            handVertexFixture = fixture.equals("hand-vertex-execution");
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
                    || fixture.equals("shadow-color-entity") || fixture.equals("water-shadow-depth-entity")
                    || entityColorFeedbackFixture || entityMrtFixture || basicVertexFixture
                    || fixture.equals("entity-vertex-execution") || blockVertexFixture;
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
            if (damagedBlockVertexFixture && damagedBlockPosition != null
                    && client.level.getBlockState(damagedBlockPosition).isSolid()) {
                client.levelRenderer.destroyBlockProgress(-9876, damagedBlockPosition, 6);
            }
            client.options.hideGui = !itemFixture;
            client.player.setXRot(entityFixture || beaconBeamFixture ? 15.0F
                    : damagedBlockVertexFixture ? 20.0F
                    : cloudFixture || skyBasicVertexFixture || skyTexturedStageFixture ? -60.0F : 35.0F);
            client.player.setYRot(45.0F);
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
                requested = true; captureActive = skyTexturedStageFixture && stage == 2;
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
                    && !skyTexturedStageFixture && !blockVertexFixture && !damagedBlockVertexFixture && !handVertexFixture
                    && !weatherVertexFixture && !particleVertexFixture && !texturedDefaultAttributesFixture
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
            var basicMrtSource = (basicVertexFixture || beaconBeamFixture) && captureActive
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
        int colored = 0;
        int opposite = 0;
        int skyMoonGreen = 0;
        int renderedItemGreen = 0;
        int glintMrtPixels = 0;
        int basicMrtPixels = 0;
        try (image; glintMrtImage; basicMrtImage) {
            Files.createDirectories(output);
            String imageName = skyTexturedStageFixture
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
                if (red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) colored++;
                if (skyTexturedStageFixture && green > 220 && red < 24 && blue < 24) skyMoonGreen++;
                if (geometryMidTexCoordFixture && green > 90 && red < green * 0.6 && blue < green * 0.6) opposite++;
                if ((currentRenderedItemFixture || glintVertexFixture || glintMrtFixture || basicVertexFixture || beaconBeamFixture)
                        && green > 220 && red < 24 && blue < 24) renderedItemGreen++;
            }
            int count = colored;
            int oppositeCount = opposite;
            int skyMoonGreenCount = skyMoonGreen;
            int renderedItemGreenCount = renderedItemGreen;
            int glintMrtPixelCount = glintMrtPixels;
            int basicMrtPixelCount = basicMrtPixels;
            completion = () -> {
                if (vanillaBaseline) {
                    finish(client, count >= 2000 ? new IllegalStateException("Unexpected shader signal in vanilla baseline: " + count) : null, count);
                } else if (active) {
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
                    VeyraRenderer.disable();
                    baselineAt = worldTicks + 80;
                    stage = 1;
                    pending = false;
                } else {
                    disabledCurrentRenderedItemGreenPixels = renderedItemGreenCount;
                    disabledGlintGreenPixels = renderedItemGreenCount;
                    disabledBasicGreenPixels = renderedItemGreenCount;
                    disabledBasicMrtPixels = basicMrtPixelCount;
                    if (beaconBeamFixture) {
                        disabledBeaconBeamGreenPixels = renderedItemGreenCount;
                        disabledBeaconBeamMrtPixels = basicMrtPixelCount;
                    }
                    int minPixels = currentRenderedItemFixture || blockVertexFixture
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
            Files.writeString(output.resolve("result.json"), "{\"status\":\"" + (failure == null ? "passed" : "failed")
                    + "\",\"renderer\":\"" + renderer + "\",\"activeMagentaPixels\":" + activePixels
                    + ",\"activeOppositeMidTexCoordPixels\":" + activeOppositeMidTexCoordPixels
                    + ",\"activeCurrentRenderedItemGreenPixels\":" + activeCurrentRenderedItemGreenPixels
                    + ",\"disabledMagentaPixels\":" + baseline
                    + ",\"disabledCurrentRenderedItemGreenPixels\":" + disabledCurrentRenderedItemGreenPixels
                    + ",\"activeGlintGreenPixels\":" + activeGlintGreenPixels
                    + ",\"disabledGlintGreenPixels\":" + disabledGlintGreenPixels
                    + ",\"activeGlintMrtPixels\":" + activeGlintMrtPixels
                    + ",\"activeBasicGreenPixels\":" + activeBasicGreenPixels
                    + ",\"disabledBasicGreenPixels\":" + disabledBasicGreenPixels
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
