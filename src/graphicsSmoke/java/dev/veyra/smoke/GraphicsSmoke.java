package dev.veyra.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import dev.veyra.shader.ShaderPack;
import dev.veyra.shader.VeyraRenderer;
import dev.veyra.shader.VeyraStorageBufferResources;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exercise a real client/world and measure the fixture's shader output against a baseline. */
public final class GraphicsSmoke {
    private static int ticks, worldTicks, stage, activePixels, baselineAt, disabledPixels, reenabledShadowMrtPixels;
    private static int lifecycleInitialWidth, lifecycleInitialHeight;
    private static int backupScreenTicks;
    private static boolean setupComputeFixture, setupResizePassed, surfaceLossRecovered;
    private static int surfaceLossProbeState;
    private static boolean pending, finished, requested, captureActive;
    private static boolean failedReplacementPassed;
    private static int nativeReadbackMagentaPixels;
    private static int activeSkyHorizonPixels;
    private static final boolean packSwitchProbe = Boolean.getBoolean("veyra.graphicsSmoke.packSwitch");
    /** Diagnostic: native screenshots of consecutive ticks of the still third-party scene before its capture. */
    private static final int frameSequence = Integer.getInteger("veyra.graphicsSmoke.frameSequence", 0);
    private static int frameSequenceCaptured;
    /** Diagnostic: keep the still third-party scene rendering for this many ticks before its capture. */
    private static final int holdTicks = Integer.getInteger("veyra.graphicsSmoke.holdTicks", 0);
    private static int heldTicks;
    private static int packSwitchPhase;
    private static ShaderPack packBeforeSwitch;
    private static long storageBytesBeforeSwitch;
    private static boolean packSwitchPassed;
    private static boolean reselectProbed;
    private static boolean guiProbeStarted;
    private static volatile String guiCaptureName;
    private static volatile Runnable guiCaptureDone;

    /** Reads back the next presented frame, GUI included, into {@code name}. */
    static void requestGuiCapture(String name, Runnable done) {
        guiCaptureName = name;
        guiCaptureDone = done;
        requested = true;
    }
    private static String expectedReplacementError = "";
    private static Runnable completion;
    private static final Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
    private static final Set<String> capturedTargetStages = new java.util.HashSet<>();
    private static final java.util.Map<String, List<ShaderPackTargetCapture.Capture>> intermediateTargetCaptures =
            new java.util.LinkedHashMap<>();
    private static boolean shadowSunwardCasterFixture, sunwardCasterPlaced;
    private static boolean entityFixtureStarted, vanillaBaseline, thirdPartyShaderpackFixture, thirdPartyScenePrepared,
            thirdPartyLifecycleFixture, thirdPartyProfileApplied, thirdPartyNaturalScene, cloudFixture, cloudSceneColorFixture,
            geometryViewSpaceFixture,
            geometryMidTexCoordFixture, terrainMaterialFixture, terrainVertexExecutionFixture,
            terrainVertexVaryingsFixture, waterMaterialFixture,
            entityMrtFixture, entityColorFeedbackFixture, entityNormalFixture, entityNormalMissingFixture,
            shadowColorEntityFixture,
            currentRenderedItemFixture, glintVertexFixture,
            glintMrtFixture, shadowColorMrtFixture, shadowColorMrtLifecycleFixture, basicVertexFixture, basicCustomImageFixture,
            entityCustomImageFixture, entityCustomTextureFixture,
            compositeCustomImageFixture, customUniformFixture,
            skyBasicVertexFixture, skyTexturedStageFixture,
            beaconBeamFixture, texturedDefaultAttributesFixture, blockVertexFixture,
            damagedBlockVertexFixture, handVertexFixture, weatherVertexFixture, particleVertexFixture,
            storageBufferFixture, normalAtlasAnimatedFixture, animatedPbrFramesChanged;
    private static volatile net.minecraft.core.BlockPos damagedBlockPosition;
    private static ShaderPack shadowMrtPack, thirdPartyPack;
    private static int activeOppositeMidTexCoordPixels, activeCurrentRenderedItemGreenPixels,
            disabledCurrentRenderedItemGreenPixels, activeGlintGreenPixels, disabledGlintGreenPixels,
            activeGlintMrtPixels, activeBasicGreenPixels, disabledBasicGreenPixels, activeBasicMrtPixels,
            disabledBasicMrtPixels, activeBeaconBeamGreenPixels, disabledBeaconBeamGreenPixels,
            activeBeaconBeamMrtPixels, disabledBeaconBeamMrtPixels,
            activeEntityCustomImageGreenPixels, disabledEntityCustomImageGreenPixels,
            activeEntityCustomTextureGreenPixels, disabledEntityCustomTextureGreenPixels,
            activeEntityNormalFallbackRedPixels, disabledEntityNormalFallbackRedPixels,
            animatedPbrMagentaFirst, animatedPbrGreenFirst, animatedPbrMagentaSecond,
            animatedPbrGreenSecond, disabledAnimatedPbrMagenta, disabledAnimatedPbrGreen,
            animatedPbrFirstRequestedFrameSlot = -1, animatedPbrSecondRequestedFrameSlot = -1,
            activeSkySunPixels, activeSkyMoonPixels, disabledSkySunPixels, disabledSkyMoonPixels;
    private static String thirdPartyProfile = "";
    private static volatile int thirdPartySurfaceHeight = Integer.MIN_VALUE;
    private static String skipReason = "";
    private static int thirdPartyNonBlackPixels, thirdPartyColorBuckets;
    private static int thirdPartyInitialWidth, thirdPartyInitialHeight;
    private static long thirdPartyStorageBufferBytes, thirdPartyInitialStorageIdentity;
    private static Set<Integer> thirdPartyInitialStorageBindings = Set.of();
    private static java.util.concurrent.CompletableFuture<?> thirdPartyReloadFuture;
    private static boolean thirdPartyResizePassed, thirdPartyNetherPassed, thirdPartyReturnPassed,
            thirdPartyDisablePassed, thirdPartyReenablePassed, thirdPartyReloadPassed,
            thirdPartyLifecycleCompleted, thirdPartyStorageBufferExpected,
            thirdPartyStorageBufferOwnerReleased, thirdPartyStorageBufferOwnerRecreated,
            thirdPartyStorageBufferManagerRecreated;
    private static volatile Throwable entityFixtureFailure;
    private GraphicsSmoke() { }

    public static void captureShaderTargetsAtStage(String stageName) {
        if (!thirdPartyShaderpackFixture || !thirdPartyScenePrepared || worldTicks < 160
                || !Boolean.getBoolean("veyra.graphicsSmoke.captureColorTargets")
                || !capturedTargetStages.add(stageName)) return;
        try {
            Renderer renderer = Renderer.getInstance();
            if (!Renderer.isRecording() || Renderer.getCommandBuffer() == null) {
                throw new IllegalStateException("Shader-pack capture requires an active Vulkan frame.");
            }
            if (renderer.getBoundRenderPass() != null) renderer.endRenderPass();
            intermediateTargetCaptures.put(stageName, ShaderPackTargetCapture.record());
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Recorded shader-pack targets at stage {}.", stageName);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("veyra-graphics-smoke").error(
                    "Could not capture shader-pack targets at stage {}.", stageName, failure);
        }
    }

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("veyra.graphicsSmoke") || finished) return;
        try {
            if (client.mouseHandler.isMouseGrabbed()
                    || org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                    != org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL)
                throw new IllegalStateException("Validation client grabbed the desktop cursor");
            for (var category : net.minecraft.sounds.SoundSource.values()) {
                if (client.options.getSoundSourceVolume(category) != 0.0F)
                    client.options.getSoundSourceOptionInstance(category).set(0.0);
            }
            if (completion != null) {
                Runnable ready = completion; completion = null; ready.run();
                if (finished) return;
            }
            if (guiProbeStarted && GuiProbe.holding()) {
                GuiProbe.tick(client, output);
                return;
            }
            if (client.level != null && client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Resuming the unattended validation world from PauseScreen");
                client.setScreen(null);
            }
            if (client.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup
                    && Files.readString(client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt")).equals("custom-uniforms")) {
                // Exercise the experimental-world screen before entering the disposable biome world.
                if (++backupScreenTicks < 20) return;
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Backup screen exercised for {} ticks", backupScreenTicks);
                var skip = Component.translatable("selectWorld.backupJoinSkipButton");
                var button = backup.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button candidate
                                && candidate.getMessage().equals(skip))
                        .map(child -> (net.minecraft.client.gui.components.Button) child).findFirst().orElseThrow();
                button.onPress(null);
            }
            if (++ticks > 2400) throw new IllegalStateException("Timed out waiting for the validation world/readback; screen=" + client.screen);
            if (client.level == null || client.player == null || client.screen != null || pending) return;
            worldTicks++;
            String fixture = Files.readString(client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt"));
            vanillaBaseline = fixture.equals("vanilla-baseline");
            thirdPartyNaturalScene = fixture.equals("third-party-natural-world");
            thirdPartyShaderpackFixture = fixture.equals("third-party-shaderpack") || thirdPartyNaturalScene;
            thirdPartyLifecycleFixture = thirdPartyShaderpackFixture
                    && Boolean.getBoolean("veyra.graphicsSmoke.thirdPartyLifecycle");
            if (thirdPartyShaderpackFixture && !thirdPartyProfileApplied) {
                thirdPartyProfile = Files.readString(client.gameDirectory.toPath()
                        .resolve("veyra/shaderpack-profile.txt")).trim();
                ShaderPack pack = VeyraRenderer.selectedPack().orElseThrow(
                        () -> new IllegalStateException("The third-party shaderpack was not selected at startup."));
                if (thirdPartyProfile.isEmpty()) throw new IllegalStateException("The third-party shaderpack profile is empty.");
                thirdPartyPack = pack;
                applyThirdPartyProfile(pack);
            }
            customUniformFixture = fixture.equals("custom-uniforms");
            cloudSceneColorFixture = fixture.equals("cloud-colortex0");
            cloudFixture = cloudSceneColorFixture || fixture.equals("cloud-texture-inputs")
                    || fixture.equals("cloud-model-view") || fixture.equals("cloud-custom-image");
            geometryViewSpaceFixture = fixture.equals("geometry-view-space");
            geometryMidTexCoordFixture = fixture.equals("geometry-mid-texcoord");
            terrainMaterialFixture = fixture.equals("terrain-block-id");
            normalAtlasAnimatedFixture = fixture.equals("normal-atlas-animated");
            terrainVertexExecutionFixture = fixture.equals("terrain-vertex-execution");
            terrainVertexVaryingsFixture = fixture.equals("terrain-vertex-varyings") || fixture.equals("custom-image-compute");
            waterMaterialFixture = fixture.equals("water-block-id");
            entityMrtFixture = fixture.equals("entity-mrt");
            entityColorFeedbackFixture = fixture.equals("entity-colortex-feedback");
            entityNormalFixture = fixture.equals("normal-entity");
            entityNormalMissingFixture = fixture.equals("normal-entity-missing");
            currentRenderedItemFixture = fixture.equals("current-rendered-item-id");
            glintVertexFixture = fixture.equals("glint-vertex-execution");
            glintMrtFixture = fixture.equals("glint-mrt-outputs");
            shadowColorEntityFixture = fixture.equals("shadow-color-entity");
            shadowSunwardCasterFixture = fixture.equals("shadow-sunward-caster");
            shadowColorMrtLifecycleFixture = fixture.equals("shadow-color-mrt-lifecycle");
            setupComputeFixture = fixture.equals("compute-setup-stage");
            shadowColorMrtFixture = fixture.equals("shadow-color-mrt") || shadowColorMrtLifecycleFixture;
            basicCustomImageFixture = fixture.equals("basic-custom-image");
            entityCustomImageFixture = fixture.equals("entity-custom-image");
            entityCustomTextureFixture = fixture.equals("entity-custom-texture");
            compositeCustomImageFixture = fixture.equals("compute-color-target") || fixture.equals("composite-custom-image") || fixture.equals("graphics-custom-image")
                    || fixture.equals("compute-ssbo-only") || fixture.equals("compute-begin-stage")
                    || fixture.equals("compute-setup-stage") || fixture.equals("compute-shadow-stage")
                    || fixture.equals("begin-fragment-stage");
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
            boolean shadowVoxelImageFixture = fixture.equals("custom-image-compute") || fixture.equals("graphics-custom-image")
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
                    || entityNormalFixture || entityNormalMissingFixture
                    || shadowColorEntityFixture || shadowColorMrtFixture
                    || fixture.equals("water-shadow-depth-entity")
                    || entityColorFeedbackFixture || entityMrtFixture || basicVertexFixture
                    || fixture.equals("entity-vertex-execution") || blockVertexFixture || entityCustomImageFixture
                    || entityCustomTextureFixture
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
            if (shadowSunwardCasterFixture && !sunwardCasterPlaced && worldTicks >= 20
                    && client.getSingleplayerServer() != null) {
                sunwardCasterPlaced = true;
                var server = client.getSingleplayerServer();
                var center = net.minecraft.core.BlockPos.containing(client.player.getX(), client.player.getY() + 30.0,
                        client.player.getZ());
                server.execute(() -> {
                    // Noon light comes straight down: this slab lies two sections toward the sun from the
                    // camera and outside its view, so only a light-volume cull keeps it in the map.
                    for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y < 2; y++)
                        server.overworld().setBlock(center.offset(x, y, z),
                                net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                });
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Placed the sunward shadow caster at {}", center);
            }
            if (entityFixtureFailure != null) throw new IllegalStateException("Entity material fixture setup failed", entityFixtureFailure);
            if (thirdPartyShaderpackFixture && !thirdPartyScenePrepared && worldTicks >= 20
                    && client.getSingleplayerServer() != null) {
                thirdPartyScenePrepared = true;
                var server = client.getSingleplayerServer();

                server.execute(() -> {
                    var world = server.overworld();
                    world.setDayTime(1000L);
                    SmokeEnvironment.freezeTime(world, server);
                    if (thirdPartyNaturalScene) {
                        thirdPartySurfaceHeight = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                        client.execute(() -> {
                            client.player.getAbilities().flying = true;
                            client.player.onUpdateAbilities();
                            client.getConnection().sendCommand("tp @s 0 " + (thirdPartySurfaceHeight + 2) + " 0");
                        });
                        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                                "Natural scene: seed=1 surfaceHeight={} dayTime=1000", thirdPartySurfaceHeight);
                        return;
                    }
                    for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                        var block = Math.floorMod(x * 3 + z, 7) == 0
                                ? net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState()
                                : Math.floorMod(x + z * 2, 9) == 0
                                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                : net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                        world.setBlock(new net.minecraft.core.BlockPos(x, 70, z), block, 3);
                    }
                    if (Boolean.getBoolean("veyra.graphicsSmoke.waterPool")) {
                        // A pool between the player and the wall: screen-space water reflections must show
                        // the wall mirrored at its far edge, not the floor around the player.
                        for (int x = -6; x <= 6; x++) for (int z = 2; z <= 6; z++) {
                            world.setBlock(new net.minecraft.core.BlockPos(x, 69, z),
                                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                            world.setBlock(new net.minecraft.core.BlockPos(x, 70, z),
                                    net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
                        }
                        LoggerFactory.getLogger("veyra-graphics-smoke").info("Placed the water pool x=-6..6 z=2..6 y=70");
                        if (Boolean.getBoolean("veyra.graphicsSmoke.poolSeagrass")) {
                            // Waterlogged plants: the water surface in these blocks must keep the water material.
                            for (int x = -3; x <= 3; x++) for (int z = 4; z <= 5; z++)
                                world.setBlock(new net.minecraft.core.BlockPos(x, 70, z),
                                        net.minecraft.world.level.block.Blocks.SEAGRASS.defaultBlockState(), 3);
                            LoggerFactory.getLogger("veyra-graphics-smoke").info("Placed pool seagrass x=-3..3 z=4..5 y=70");
                        }
                        if (Boolean.getBoolean("veyra.graphicsSmoke.poolPlants")) {
                            // Plants on the far shore: their reflections and the blocks beside them must stay intact.
                            var plants = new net.minecraft.world.level.block.state.BlockState[]{
                                    net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(),
                                    net.minecraft.world.level.block.Blocks.POPPY.defaultBlockState(),
                                    net.minecraft.world.level.block.Blocks.FERN.defaultBlockState(),
                                    net.minecraft.world.level.block.Blocks.DANDELION.defaultBlockState()};
                            for (int x = -6; x <= 6; x++) {
                                world.setBlock(new net.minecraft.core.BlockPos(x, 70, 7),
                                        net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                                world.setBlock(new net.minecraft.core.BlockPos(x, 71, 7), plants[Math.floorMod(x, plants.length)], 3);
                            }
                            LoggerFactory.getLogger("veyra-graphics-smoke").info("Placed pool plants x=-6..6 z=7 y=71");
                        }
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
                    if (Boolean.getBoolean("veyra.graphicsSmoke.foliage")) {
                        // Cutout foliage in front of the wall: leaves of several species and a row of grass.
                        var foliage = new net.minecraft.world.level.block.state.BlockState[]{
                                net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                                net.minecraft.world.level.block.Blocks.BIRCH_LEAVES.defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                                net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES.defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)};
                        for (int x = -5; x <= 5; x++) for (int y = 71; y <= 73; y++)
                            world.setBlock(new net.minecraft.core.BlockPos(x, y, 5), foliage[Math.floorMod(x, 3)], 3);
                        for (int x = -8; x <= 8; x++)
                            world.setBlock(new net.minecraft.core.BlockPos(x, 71, 3),
                                    net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    }
                    world.setBlock(new net.minecraft.core.BlockPos(-9, 71, 8),
                            net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
                    world.setBlock(new net.minecraft.core.BlockPos(9, 71, 8),
                            net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState(), 3);
                    var signPos = new net.minecraft.core.BlockPos(0, 71, 8);
                    var signState = net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState()
                            .setValue(StandingSignBlock.ROTATION, 8);
                    world.setBlock(signPos, signState, 3);
                    if (!(world.getBlockEntity(signPos) instanceof SignBlockEntity sign)
                            || !sign.setText(sign.getFrontText()
                                    .setMessage(0, Component.literal("VEYRA").withStyle(ChatFormatting.GREEN))
                                    .setMessage(1, Component.literal("SIGN TEST").withStyle(ChatFormatting.GREEN)), true)) {
                        throw new IllegalStateException("Could not prepare the real sign-text fixture.");
                    }
                    world.sendBlockUpdated(signPos, signState, signState, 3);
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Server fixture states: floor={} wall={} dayTime={}",
                            world.getBlockState(new net.minecraft.core.BlockPos(0, 70, 0)),
                            world.getBlockState(signPos), world.getDayTime());
                    client.execute(() -> client.getConnection().sendCommand("tp @s 0 72 0"));
                });
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        thirdPartyNaturalScene ? "Prepared natural shaderpack fixture with seed=1 and fixed daylight."
                                : "Prepared the real shaderpack fixture with daylight, a mixed block field, a lit material wall and a sign.");
            }
            if (damagedBlockVertexFixture && damagedBlockPosition != null
                    && client.level.getBlockState(damagedBlockPosition).isSolid()) {
                client.levelRenderer.destroyBlockProgress(-9876, damagedBlockPosition, 6);
            }
            client.options.hideGui = !itemFixture;
            client.player.setXRot(thirdPartyNaturalScene ? 15.0F : thirdPartyShaderpackFixture ? -2.0F
                    : skyBasicVertexFixture ? 0.0F
                    : entityFixture || beaconBeamFixture ? 15.0F
                    : damagedBlockVertexFixture ? 20.0F
                    : cloudFixture || skyBasicVertexFixture || skyTexturedStageFixture ? -60.0F : 35.0F);
            client.player.setYRot(thirdPartyNaturalScene ? 135.0F : thirdPartyShaderpackFixture ? 0.0F : 45.0F);
            if (vanillaBaseline && worldTicks >= 240) {
                if (VeyraRenderer.isRenderingEnabled() || VeyraRenderer.selectedPack().isPresent())
                    throw new IllegalStateException("Vanilla baseline unexpectedly selected a shaderpack");
                pending = true; requested = true; captureActive = false;
            } else if (!vanillaBaseline && thirdPartyShaderpackFixture && stage == 0 && worldTicks >= 160) {
                if (thirdPartyLifecycleFixture) requireThirdPartyActive("initial profile capture");
                if (thirdPartyNaturalScene && thirdPartySurfaceHeight == Integer.MIN_VALUE) return;
                var fixtureFloor = new net.minecraft.core.BlockPos(0, thirdPartyNaturalScene ? thirdPartySurfaceHeight - 1 : 70, 0);
                var fixtureFloorState = client.level.getBlockState(fixtureFloor);
                var fixtureWall = new net.minecraft.core.BlockPos(0, 71, 8);
                var fixtureWallState = client.level.getBlockState(fixtureWall);
                if (Math.abs(client.player.getX() - 0.5) > 0.25
                        || client.player.getY() < (thirdPartyNaturalScene ? thirdPartySurfaceHeight + 1.9 : 70.9)
                        || client.player.getY() > (thirdPartyNaturalScene ? thirdPartySurfaceHeight + 2.25 : 72.25)
                        || Math.abs(client.player.getZ() - 0.5) > 0.25 || client.level.getDayTime() != 1000L
                        || fixtureFloorState.isAir()
                        || !thirdPartyNaturalScene && !fixtureWallState.is(net.minecraft.world.level.block.Blocks.OAK_SIGN)) {
                    if (worldTicks >= 400) {
                        throw new IllegalStateException("The real shaderpack capture scene is not ready: pos="
                                + client.player.position() + ", dayTime=" + client.level.getDayTime()
                                + ", floor=" + fixtureFloorState + ", wall=" + fixtureWallState);
                    }
                    return;
                }
                if (heldTicks < holdTicks) {
                    heldTicks++;
                    return;
                }
                if (IconifyProbe.holding()) {
                    IconifyProbe.tick(client);
                    return;
                }
                if (GuiProbe.holding() && !guiProbeStarted) {
                    guiProbeStarted = true;
                    GuiProbe.tick(client, output);
                    return;
                }
                if (Boolean.getBoolean("veyra.graphicsSmoke.reselectProbe") && !reselectProbed) {
                    // Times the shader-screen path: a fresh configuration snapshot selected on the render thread.
                    reselectProbed = true;
                    var pack = VeyraRenderer.selectedPack().orElseThrow();
                    long started = System.nanoTime();
                    String result = VeyraRenderer.selectWithSavedOptions(pack);
                    LoggerFactory.getLogger("veyra-graphics-smoke").info("RESELECT {} ms: {}",
                            (System.nanoTime() - started) / 1_000_000L, result);
                    return;
                }
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Capture request: tick={} pos=({}, {}, {}) rotation=({}, {}) dayTime={} floor={} wall={}",
                        worldTicks, client.player.getX(), client.player.getY(), client.player.getZ(),
                        client.player.getXRot(), client.player.getYRot(), client.level.getDayTime(),
                        fixtureFloorState, fixtureWallState);
                pending = true;
                requested = true;
                captureActive = true;
            } else if (!vanillaBaseline && !thirdPartyShaderpackFixture && stage == 0 && worldTicks >= 160) {
                if (packSwitchProbe && packSwitchPhase > 0 && worldTicks < baselineAt) return;
                if (Boolean.getBoolean("veyra.graphicsSmoke.failedReplacement") && !failedReplacementPassed)
                    verifyFailedReplacement(client);
                if (!VeyraRenderer.isRenderingEnabled()
                        || !VeyraRenderer.getLastError().equals(expectedReplacementError))
                    throw new IllegalStateException("Shader pipeline failed: " + VeyraRenderer.getLastError());
                if (cloudSceneColorFixture && !VeyraRenderer.hasCapturedCloudSceneColor())
                    throw new IllegalStateException("The cloud scene-color sampler was not captured before drawing clouds");
                pending = true;
                if (normalAtlasAnimatedFixture)
                    animatedPbrFirstRequestedFrameSlot = currentAnimatedPbrFrameSlot(client);
                requested = true; captureActive = true;
            } else if (normalAtlasAnimatedFixture && stage == 2 && worldTicks >= baselineAt
                    && currentAnimatedPbrFrameSlot(client) == 1 - animatedPbrFirstRequestedFrameSlot) {
                animatedPbrSecondRequestedFrameSlot = currentAnimatedPbrFrameSlot(client);
                pending = true;
                requested = true;
                captureActive = true;
            } else if (thirdPartyLifecycleFixture && stage >= 1 && worldTicks >= baselineAt) {
                if (stage == 1 && surfaceLossProbeHolding()) return;
                if (stage == 2) {
                    if (!client.level.dimension().equals(net.minecraft.world.level.Level.NETHER)) return;
                    if (VeyraRenderer.selectedPack().map(pack -> pack.configuration().dimension() != -1).orElse(true)) {
                        throw new IllegalStateException("The real shaderpack profile did not switch to the Nether configuration.");
                    }
                } else if (stage == 3) {
                    if (!client.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
                    if (VeyraRenderer.selectedPack().map(pack -> pack.configuration().dimension() != 0).orElse(true)) {
                        throw new IllegalStateException("The real shaderpack profile did not restore its Overworld configuration.");
                    }
                } else if (stage == 4) {
                    if (VeyraRenderer.isRenderingEnabled() || VeyraStorageBufferResources.active() != null) {
                        throw new IllegalStateException("Disabling the real shaderpack did not release its active GPU resource owners.");
                    }
                } else if (stage == 6) {
                    if (thirdPartyReloadFuture == null) throw new IllegalStateException("The resource reload future was not created.");
                    if (!thirdPartyReloadFuture.isDone()) return;
                    thirdPartyReloadFuture.join();
                    thirdPartyReloadFuture = null;
                }
                if (stage != 4) requireThirdPartyActive("lifecycle stage " + stage);
                pending = true;
                requested = true;
                captureActive = stage != 4;
            } else if (!vanillaBaseline && !thirdPartyShaderpackFixture
                    && (stage == 1 || stage == 2) && worldTicks >= baselineAt
                    && !(normalAtlasAnimatedFixture && stage == 2)) {
                if (setupComputeFixture && stage == 2 && surfaceLossProbeHolding()) return;
                pending = true;
                requested = true; captureActive = (skyTexturedStageFixture || shadowColorMrtFixture
                        || setupComputeFixture) && stage == 2;
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
        java.util.List<ShaderPackTargetCapture.Capture> targetCaptures = java.util.List.of();
        java.util.Map<String, java.util.List<ShaderPackTargetCapture.Capture>> phaseCaptures = java.util.Map.of();
        try {
            Renderer renderer = Renderer.getInstance();
            renderer.endRenderPass();
            if ((thirdPartyShaderpackFixture || customUniformFixture) && stage == 0) captureComparisonMatrices();
            if (thirdPartyShaderpackFixture && VeyraRenderer.isRenderingEnabled()
                    && Boolean.getBoolean("veyra.graphicsSmoke.captureColorTargets")) {
                targetCaptures = ShaderPackTargetCapture.record();
            }
            phaseCaptures = new java.util.LinkedHashMap<>(intermediateTargetCaptures);
            intermediateTargetCaptures.clear();
            if (captureActive && Boolean.getBoolean("veyra.graphicsSmoke.computeProbe")) {
                computeProbe = ComputeImageProbe.record();
            }
            var source = renderer.getMainPass().getMainFramebuffer().getColorAttachment();
            if ((source.usage & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0)
                throw new IllegalStateException("Surface image does not support frame readback");
            // These fixtures validate their diagnostic signal in the presented colortex0 image.
            // The entity feedback fixture also writes colortex4, but does not use it as its signal.
            var auxiliary = VeyraRenderer.isRenderingEnabled() && packSwitchPhase != 1 && !cloudFixture && !geometryViewSpaceFixture
                    && !geometryMidTexCoordFixture && !terrainMaterialFixture && !waterMaterialFixture
                    && !terrainVertexExecutionFixture && !terrainVertexVaryingsFixture
                    && !entityColorFeedbackFixture && !currentRenderedItemFixture && !glintVertexFixture
                    && !glintMrtFixture && !basicVertexFixture && !skyBasicVertexFixture && !beaconBeamFixture
                    && !entityCustomImageFixture && !compositeCustomImageFixture
                    && !skyTexturedStageFixture && !blockVertexFixture && !damagedBlockVertexFixture && !handVertexFixture
                    && !weatherVertexFixture && !particleVertexFixture && !texturedDefaultAttributesFixture
                    && !entityCustomTextureFixture
                    && !storageBufferFixture && !shadowSunwardCasterFixture
                    ? VeyraRenderer.geometryColorAttachment(1) : null;
            if (customUniformFixture && captureActive && packSwitchPhase != 1) {
                if (backupScreenTicks < 20) throw new IllegalStateException("The experimental-world backup screen was not exercised");
                int targetFormat = VeyraRenderer.geometryColorAttachment(1).format;
                if (targetFormat != VK_FORMAT_A2B10G10R10_UNORM_PACK32 && targetFormat != VK_FORMAT_R16G16B16A16_SFLOAT)
                    throw new IllegalStateException("The legacy gdepthFormat directive was not applied: Vulkan format " + targetFormat);
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Legacy gdepthFormat physical storage: {}", targetFormat);
                int hdrFormat = VeyraRenderer.geometryColorAttachment(2).format;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var properties = org.lwjgl.vulkan.VkFormatProperties.calloc(stack);
                    vkGetPhysicalDeviceFormatProperties(DeviceManager.physicalDevice, VK_FORMAT_B10G11R11_UFLOAT_PACK32, properties);
                    int required = VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT
                            | VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
                    int expected = (properties.optimalTilingFeatures() & required) == required
                            ? VK_FORMAT_B10G11R11_UFLOAT_PACK32 : VK_FORMAT_R16G16B16A16_SFLOAT;
                    if (hdrFormat != expected) throw new IllegalStateException("R11F_G11F_B10F did not use supported storage: " + hdrFormat);
                }
                LoggerFactory.getLogger("veyra-graphics-smoke").info("R11F_G11F_B10F physical storage: {}", hdrFormat);
            }
            for (var custom : dev.veyra.shader.CustomUniforms.active().uniforms()) {
                LoggerFactory.getLogger("veyra-graphics-smoke").info("Custom uniform {} ({}) = {} / {}", custom.name(), custom.type(),
                        dev.veyra.shader.CustomUniforms.active().floatValue(custom.name()), dev.veyra.shader.CustomUniforms.active().intValue(custom.name()));
            }
            LoggerFactory.getLogger("veyra-graphics-smoke").info("Frame evidence: active={} main={} auxiliary={}",
                    captureActive, Long.toHexString(source.getId()), auxiliary == null ? "disabled" : Long.toHexString(auxiliary.getId()));
            var surface = renderer.getSwapChain();
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Capture surface: stage={} extent={}x{} hasImages={} acquired={} iconified={} source={}x{} format={}",
                    stage, surface.getWidth(), surface.getHeight(), surface.hasImages(), SmokeEnvironment.isAcquired(surface),
                    org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED),
                    source.width, source.height, source.format);
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
            java.util.List<ShaderPackTargetCapture.Capture> completedTargets = java.util.List.copyOf(targetCaptures);
            java.util.Map<String, java.util.List<ShaderPackTargetCapture.Capture>> completedPhaseTargets =
                    java.util.Map.copyOf(phaseCaptures);
            boolean active = captureActive;
            MemoryManager.getInstance().addFrameOp(() -> {
                try {
                    if (completedComputeProbe != null) completedComputeProbe.verify();
                    for (ShaderPackTargetCapture.Capture target : completedTargets) {
                        target.write(output.resolve("shaderpack-targets"));
                    }
                    for (var phaseCapture : completedPhaseTargets.entrySet()) {
                        for (ShaderPackTargetCapture.Capture target : phaseCapture.getValue()) {
                            target.write(output.resolve("shaderpack-targets-" + phaseCapture.getKey()));
                        }
                    }
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
                    String guiName = guiCaptureName;
                    if (guiName != null) {
                        // GuiProbe frames: write the swapchain image with its GUI and skip the scene checks.
                        guiCaptureName = null;
                        try (image) {
                            Files.createDirectories(output);
                            image.writeToFile(output.resolve(guiName));
                        }
                        Runnable done = guiCaptureDone;
                        guiCaptureDone = null;
                        if (done != null) client.execute(done);
                        return;
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
                    completedTargets.forEach(ShaderPackTargetCapture.Capture::close);
                    completedPhaseTargets.values().forEach(captures ->
                            captures.forEach(ShaderPackTargetCapture.Capture::close));
                    completed.scheduleFree();
                    if (completedGlintMrt != null) completedGlintMrt.scheduleFree();
                    if (completedBasicMrt != null) completedBasicMrt.scheduleFree();
                }
            });
            if (active && packSwitchPhase != 1 && Boolean.getBoolean("veyra.graphicsSmoke.failedReplacement")) {
                net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        int matches = 0;
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                            int pixel = image.getPixel(x, y);
                            if ((pixel >> 16 & 255) > 220 && (pixel >> 8 & 255) < 24 && (pixel & 255) > 180) matches++;
                        }
                        if (matches < 10000)
                            throw new IllegalStateException("Native screenshot lost the active shader frame: " + matches);
                        nativeReadbackMagentaPixels = matches;
                    }
                });
            }
        } catch (Throwable failure) {
            if (computeProbe != null) computeProbe.close();
            targetCaptures.forEach(ShaderPackTargetCapture.Capture::close);
            phaseCaptures.values().forEach(captures ->
                    captures.forEach(ShaderPackTargetCapture.Capture::close));
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
        int animatedPbrGreen = 0;
        int normalFallbackRed = 0;
        int opposite = 0;
        int skyMoonGreen = 0;
        int renderedItemGreen = 0;
        int glintMrtPixels = 0;
        int basicMrtPixels = 0;
        int nonBlackPixels = 0;
        int colorBuckets = 0;
        int switchGreenPixels = 0;
        int skyHorizonPixels = 0;
        boolean[] colors = new boolean[512];
        try (image; glintMrtImage; basicMrtImage) {
            Files.createDirectories(output);
                    String imageName = thirdPartyShaderpackFixture && thirdPartyLifecycleFixture
                    ? switch (stage) {
                        case 0 -> "third-party-pack-initial.png";
                        case 1 -> "third-party-pack-resized.png";
                        case 2 -> "third-party-pack-nether.png";
                        case 3 -> "third-party-pack-overworld-return.png";
                        case 4 -> "third-party-pack-disabled.png";
                        case 5 -> "third-party-pack-reenabled.png";
                        case 6 -> "third-party-pack-reloaded.png";
                        default -> "third-party-pack-stage-" + stage + ".png";
                    }
                    : thirdPartyShaderpackFixture ? "third-party-pack-active.png"
                    : storageBufferFixture ? active ? "storage-buffer-active.png" : "storage-buffer-disabled.png"
                    : shadowColorMrtFixture && stage == 2 ? "shadow-mrt-reenabled.png"
                    : shadowColorMrtLifecycleFixture && stage == 3 ? "shadow-mrt-resized.png"
                    : shadowColorMrtLifecycleFixture && stage == 6 ? "shadow-mrt-dimension-return.png"
                    : normalAtlasAnimatedFixture && stage == 0 ? "animated-pbr-first.png"
                    : normalAtlasAnimatedFixture && stage == 2 ? "animated-pbr-second.png"
                    : skyTexturedStageFixture
                    ? active && stage == 2 ? "skytextured-moon-active.png"
                            : active ? "skytextured-sun-active.png" : "skytextured-disabled.png"
                    : setupComputeFixture && active && stage == 2 ? "setup-resized.png"
                    : active ? "mrt-active.png" : "shaders-disabled.png";
            image.writeToFile(output.resolve(imageName));
            if (packSwitchProbe && active) image.writeToFile(output.resolve("pack-switch-" + packSwitchPhase + ".png"));
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
                if (packSwitchProbe && (pixel >> 16 & 255) < 24 && (pixel >> 8 & 255) > 220 && (pixel & 255) < 24)
                    switchGreenPixels++;
                int red = (pixel >>> 16) & 255, green = (pixel >>> 8) & 255, blue = pixel & 255;
                if (skyBasicVertexFixture && y >= image.getHeight() / 2 - 4 && y < image.getHeight() / 2
                        && red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) skyHorizonPixels++;
                if (Math.max(red, Math.max(green, blue)) > 12) {
                    nonBlackPixels++;
                    int bucket = ((red >>> 5) << 6) | ((green >>> 5) << 3) | (blue >>> 5);
                    if (!colors[bucket]) { colors[bucket] = true; colorBuckets++; }
                }
                if (shadowColorEntityFixture
                        ? red >= 200 && green <= 20 && blue >= 150 && red > blue
                        : red > 90 && blue > 75 && red > green * 1.4 && blue > green * 1.3) colored++;
                if (normalAtlasAnimatedFixture && green > 180 && red < 30 && blue < 30) animatedPbrGreen++;
                if (entityNormalMissingFixture && red > 220 && green < 70 && blue < 70) normalFallbackRed++;
                if (skyTexturedStageFixture && green > 220 && red < 24 && blue < 24) skyMoonGreen++;
                if (geometryMidTexCoordFixture && green > 90 && red < green * 0.6 && blue < green * 0.6) opposite++;
                if ((currentRenderedItemFixture || glintVertexFixture || glintMrtFixture || basicVertexFixture
                        || beaconBeamFixture || entityCustomImageFixture || entityCustomTextureFixture)
                        && green > 220 && red < 24 && blue < 24) renderedItemGreen++;
            }
            int count = colored;
            int animatedPbrGreenCount = animatedPbrGreen;
            int normalFallbackRedCount = normalFallbackRed;
            int oppositeCount = opposite;
            int skyMoonGreenCount = skyMoonGreen;
            int renderedItemGreenCount = renderedItemGreen;
            int glintMrtPixelCount = glintMrtPixels;
            int basicMrtPixelCount = basicMrtPixels;
            int nonBlackPixelCount = nonBlackPixels;
            int colorBucketCount = colorBuckets;
            int switchGreenCount = switchGreenPixels;
            int skyHorizonCount = skyHorizonPixels;
            completion = () -> {
                if (packSwitchProbe && active && stage == 0 && advancePackSwitch(client, count, switchGreenCount)) return;
                if (thirdPartyShaderpackFixture) {
                    thirdPartyNonBlackPixels = nonBlackPixelCount;
                    thirdPartyColorBuckets = colorBucketCount;
                    if (thirdPartyLifecycleFixture) {
                        completeThirdPartyLifecycleCapture(client, frameWidth, frameHeight, active,
                                nonBlackPixelCount, colorBucketCount, count);
                        return;
                    }
                    if (frameSequenceCaptured < frameSequence) {
                        // Diagnostic sequence of the still scene: keep each readback and request the next frame.
                        try {
                            Files.copy(output.resolve("third-party-pack-active.png"),
                                    output.resolve(String.format("sequence-%02d.png", frameSequenceCaptured++)),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                        pending = false;
                        return;
                    }
                    Throwable failure = !thirdPartyProfileApplied || !VeyraRenderer.isRenderingEnabled()
                            || !VeyraRenderer.getLastError().isEmpty()
                            ? new IllegalStateException("The real shaderpack/profile was no longer active: "
                                    + VeyraRenderer.getLastError())
                            : (nonBlackPixelCount < 10000 || colorBucketCount < 24)
                                    && !Boolean.getBoolean("veyra.graphicsSmoke.allowUniformFrame") // debug views of single buffers
                            ? new IllegalStateException("The real shaderpack frame was empty or nearly uniform: nonBlack="
                                    + nonBlackPixelCount + ", colorBuckets=" + colorBucketCount)
                            : null;
                    activePixels = count;
                    finish(client, failure, -1);
                } else if (vanillaBaseline) {
                    finish(client, count >= 2000 ? new IllegalStateException("Unexpected shader signal in vanilla baseline: " + count) : null, count);
                } else if (active) {
                    if (setupComputeFixture) {
                        if (count < frameWidth * frameHeight * 0.9) {
                            finish(client, new IllegalStateException("Setup compute sizing/count/viewport signal failed at stage "
                                    + stage + ": " + count + " pixels"), -1);
                            return;
                        }
                        if (stage == 0) {
                            lifecycleInitialWidth = frameWidth;
                            lifecycleInitialHeight = frameHeight;
                            int resizedWidth = frameWidth == 800 ? 960 : 800;
                            client.getWindow().setWindowed(resizedWidth, resizedWidth * 9 / 16);
                            stage = 2;
                            baselineAt = worldTicks + 100;
                            pending = false;
                            return;
                        }
                        if (frameWidth == lifecycleInitialWidth && frameHeight == lifecycleInitialHeight) {
                            finish(client, new IllegalStateException("Setup fixture window did not resize"), -1);
                            return;
                        }
                        requireRecoveredSurface(frameWidth, frameHeight);
                        setupResizePassed = true;
                    }
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
                    if (skyBasicVertexFixture) activeSkyHorizonPixels = skyHorizonCount;
                    if (normalAtlasAnimatedFixture && (stage == 0 || stage == 2)) {
                        if (stage == 0) {
                            animatedPbrMagentaFirst = count;
                            animatedPbrGreenFirst = animatedPbrGreenCount;
                            boolean firstMagenta = animatedPbrMagentaFirst >= 100 && animatedPbrGreenFirst < 100;
                            boolean firstGreen = animatedPbrGreenFirst >= 100 && animatedPbrMagentaFirst < 100;
                            boolean firstMatchesBase = animatedPbrFirstRequestedFrameSlot == 0
                                    ? firstMagenta : animatedPbrFirstRequestedFrameSlot == 1 && firstGreen;
                            if (!firstMatchesBase) {
                                finish(client, new IllegalStateException("The animated PBR fixture did not show a unique first frame: magenta="
                                        + animatedPbrMagentaFirst + ", green=" + animatedPbrGreenFirst
                                        + ", requested base frame=" + animatedPbrFirstRequestedFrameSlot), -1);
                                return;
                            }
                            stage = 2;
                            baselineAt = worldTicks + 1;
                            pending = false;
                            return;
                        }
                        animatedPbrMagentaSecond = count;
                        animatedPbrGreenSecond = animatedPbrGreenCount;
                        boolean secondMagenta = animatedPbrMagentaSecond >= 100 && animatedPbrGreenSecond < 100;
                        boolean secondGreen = animatedPbrGreenSecond >= 100 && animatedPbrMagentaSecond < 100;
                        boolean firstMagenta = animatedPbrMagentaFirst >= 100 && animatedPbrGreenFirst < 100;
                        boolean firstGreen = animatedPbrGreenFirst >= 100 && animatedPbrMagentaFirst < 100;
                        animatedPbrFramesChanged = firstMagenta ? secondGreen : firstGreen && secondMagenta;
                        boolean secondMatchesBase = animatedPbrSecondRequestedFrameSlot == 0
                                ? secondMagenta : animatedPbrSecondRequestedFrameSlot == 1 && secondGreen;
                        if (!animatedPbrFramesChanged || !secondMatchesBase) {
                            finish(client, new IllegalStateException("Animated normal and specular maps did not change with their base atlas sprite: first="
                                    + animatedPbrMagentaFirst + "/" + animatedPbrGreenFirst + ", second="
                                    + animatedPbrMagentaSecond + "/" + animatedPbrGreenSecond + ", requested base frame="
                                    + animatedPbrFirstRequestedFrameSlot + " then " + animatedPbrSecondRequestedFrameSlot), -1);
                            return;
                        }
                        VeyraRenderer.disable();
                        baselineAt = worldTicks + 80;
                        stage = 1;
                        pending = false;
                        return;
                    }
                    activeEntityNormalFallbackRedPixels = normalFallbackRedCount;
                    activeOppositeMidTexCoordPixels = oppositeCount;
                    activeCurrentRenderedItemGreenPixels = renderedItemGreenCount;
                    activeGlintGreenPixels = renderedItemGreenCount;
                    activeGlintMrtPixels = glintMrtPixelCount;
                    activeBasicGreenPixels = renderedItemGreenCount;
                    activeBasicMrtPixels = basicMrtPixelCount;
                    if (entityCustomImageFixture) activeEntityCustomImageGreenPixels = renderedItemGreenCount;
                    if (entityCustomTextureFixture) activeEntityCustomTextureGreenPixels = renderedItemGreenCount;
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
                    disabledEntityNormalFallbackRedPixels = normalFallbackRedCount;
                    disabledGlintGreenPixels = renderedItemGreenCount;
                    disabledBasicGreenPixels = renderedItemGreenCount;
                    disabledBasicMrtPixels = basicMrtPixelCount;
                    if (entityCustomImageFixture) disabledEntityCustomImageGreenPixels = renderedItemGreenCount;
                    if (entityCustomTextureFixture) disabledEntityCustomTextureGreenPixels = renderedItemGreenCount;
                    if (beaconBeamFixture) {
                        disabledBeaconBeamGreenPixels = renderedItemGreenCount;
                        disabledBeaconBeamMrtPixels = basicMrtPixelCount;
                    }
                    int minPixels = currentRenderedItemFixture || blockVertexFixture || entityCustomImageFixture
                            || entityCustomTextureFixture
                            || damagedBlockVertexFixture || handVertexFixture || weatherVertexFixture || particleVertexFixture
                            || texturedDefaultAttributesFixture || shadowSunwardCasterFixture ? 100 : 2000;
                    Throwable failure;
                    if (normalAtlasAnimatedFixture) {
                        disabledAnimatedPbrMagenta = count;
                        disabledAnimatedPbrGreen = animatedPbrGreenCount;
                        failure = !animatedPbrFramesChanged
                                || animatedPbrMagentaFirst + animatedPbrMagentaSecond < 100
                                || animatedPbrGreenFirst + animatedPbrGreenSecond < 100
                                || disabledAnimatedPbrMagenta * 4 >= animatedPbrMagentaFirst + animatedPbrMagentaSecond
                                || disabledAnimatedPbrGreen * 4 >= animatedPbrGreenFirst + animatedPbrGreenSecond
                                ? new IllegalStateException("Animated PBR atlas signals did not alternate or clear after disable: magenta="
                                + animatedPbrMagentaFirst + "/" + animatedPbrMagentaSecond + "/" + disabledAnimatedPbrMagenta
                                + ", green=" + animatedPbrGreenFirst + "/" + animatedPbrGreenSecond + "/" + disabledAnimatedPbrGreen)
                                : null;
                    } else if (skyTexturedStageFixture) {
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
                    } else if (entityCustomTextureFixture) {
                        failure = activeEntityCustomTextureGreenPixels < 100
                                || disabledEntityCustomTextureGreenPixels * 4 >= activeEntityCustomTextureGreenPixels
                                ? new IllegalStateException("The entity custom-texture signal was absent or unchanged: scene green active/disabled="
                                        + activeEntityCustomTextureGreenPixels + "/" + disabledEntityCustomTextureGreenPixels) : null;
                    } else if (entityNormalFixture) {
                        failure = activePixels < 100 || count * 4 >= activePixels
                                ? new IllegalStateException("The entity authored normal-map signal was absent or unchanged: active="
                                        + activePixels + ", disabled=" + count) : null;
                    } else if (entityNormalMissingFixture) {
                        failure = activeEntityNormalFallbackRedPixels < 100
                                || disabledEntityNormalFallbackRedPixels * 4 >= activeEntityNormalFallbackRedPixels
                                ? new IllegalStateException("The missing entity normal-map fallback was absent or unchanged: active red="
                                        + activeEntityNormalFallbackRedPixels + ", disabled red="
                                        + disabledEntityNormalFallbackRedPixels) : null;
                    } else if (shadowColorEntityFixture) {
                        failure = activePixels < 100 || count * 4 >= activePixels
                                ? new IllegalStateException("The entity-written shadow color marker was absent or unchanged: active="
                                        + activePixels + ", disabled=" + count) : null;
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
                        failure = activePixels < 2000 || activeSkyHorizonPixels < 1000 || count * 4 >= activePixels
                                ? new IllegalStateException("The sky-basic vertex-stage signal was absent or unchanged: active="
                                + activePixels + ", horizon=" + activeSkyHorizonPixels + ", disabled=" + count) : null;
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

    private static void applyThirdPartyProfile(ShaderPack pack) {
        try {
            if (thirdPartyProfile.equals("__DEFAULT__")) {
                if (!pack.configuration().options().isEmpty() && !Boolean.getBoolean("veyra.graphicsSmoke.allowOptionOverrides")
                        || !VeyraRenderer.isRenderingEnabled()
                        || !VeyraRenderer.getLastError().isEmpty()) {
                    throw new IllegalStateException("Default shaderpack smoke requires an active pack without option overrides: "
                            + VeyraRenderer.getLastError());
                }
                thirdPartyPack = pack;
                thirdPartyProfileApplied = true;
                LoggerFactory.getLogger("veyra-graphics-smoke").info(
                        "Using unmodified shaderpack defaults from {}", pack.name());
                return;
            }
            var definitions = pack.readOptions();
            var profile = definitions.profiles().get(thirdPartyProfile);
            if (profile == null || profile.isEmpty()) {
                throw new IllegalStateException("Shaderpack profile was not found or has no options: " + thirdPartyProfile);
            }
            String applied = VeyraRenderer.applyOptions(pack, profile);
            ShaderPack configured = VeyraRenderer.selectedPack().orElseThrow();
            var effective = configured.readOptions().effective(configured.configuration().options());
            boolean profileMatches = true;
            for (var option : profile.entrySet()) {
                if (!option.getValue().equals(effective.get(option.getKey()))) {
                    profileMatches = false;
                    break;
                }
            }
            // A pack may already start with the selected profile's effective values. In that case
            // applyOptions reports a no-op; accept it only after verifying every profile entry.
            boolean alreadyActive = applied.equals("Shader options unchanged.") && profileMatches;
            boolean appliedSuccessfully = applied.startsWith("Enabled ");
            if ((!appliedSuccessfully && !alreadyActive) || !VeyraRenderer.isRenderingEnabled()
                    || !VeyraRenderer.getLastError().isEmpty()) {
                throw new IllegalStateException("Could not apply " + thirdPartyProfile + " profile: " + applied
                        + " (" + VeyraRenderer.getLastError() + ")");
            }
            if (!profileMatches) {
                throw new IllegalStateException("One or more options from profile " + thirdPartyProfile
                        + " did not take effect.");
            }
            // applyOptions replaces the configured pack instance; retain that selected snapshot so
            // a later disable/re-enable restores this profile rather than the pre-profile object.
            thirdPartyPack = configured;
            thirdPartyProfileApplied = true;
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Applied real shaderpack profile {} with {} options from {}", thirdPartyProfile,
                    profile.size(), configured.name());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read the real shaderpack profile " + thirdPartyProfile, exception);
        }
    }

    private static VeyraStorageBufferResources requireThirdPartyActive(String phase) {
        if (!thirdPartyProfileApplied || !VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty()) {
            throw new IllegalStateException("The real shaderpack/profile is not active during " + phase + ": "
                    + VeyraRenderer.getLastError());
        }
        ShaderPack selected = VeyraRenderer.selectedPack().orElseThrow(
                () -> new IllegalStateException("The real shaderpack selection disappeared during " + phase + "."));
        Set<Integer> declaredBindings;
        try {
            declaredBindings = Set.copyOf(selected.readStorageBufferProperties().buffers().keySet());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not inspect the selected shaderpack storage-buffer declarations during "
                    + phase + ".", exception);
        }
        VeyraStorageBufferResources resources = VeyraStorageBufferResources.active();
        if (declaredBindings.isEmpty()) {
            if (resources != null && (!resources.properties().buffers().isEmpty() || resources.allocatedBytes() != 0)) {
                throw new IllegalStateException("The real shaderpack declares no storage buffers, but its active Veyra manager has "
                        + resources.properties().buffers().size() + " declarations and " + resources.allocatedBytes()
                        + " allocated bytes during " + phase + ".");
            }
            if (stage == 0 && resources != null) thirdPartyInitialStorageIdentity = resources.identity();
            if (stage >= 5 && resources != null && thirdPartyInitialStorageIdentity != 0) {
                if (resources.identity() == thirdPartyInitialStorageIdentity) {
                    throw new IllegalStateException("Re-enabling the real shaderpack reused its storage-buffer manager after disable().");
                }
                thirdPartyStorageBufferManagerRecreated = true;
            }
            thirdPartyStorageBufferBytes = 0;
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "The real shaderpack declares no storage buffers during lifecycle stage {}; its active manager owns no GPU allocation",
                    stage);
            return null;
        }
        if (resources == null || resources.allocatedBytes() <= 0
                || !resources.properties().buffers().keySet().equals(declaredBindings)) {
            throw new IllegalStateException("The real shaderpack's declared storage buffers " + declaredBindings
                    + " do not match its active Veyra allocation during " + phase + ".");
        }
        thirdPartyStorageBufferExpected = true;
        if (stage == 0) {
            thirdPartyInitialStorageBindings = declaredBindings;
            thirdPartyInitialStorageIdentity = resources.identity();
        }
        if (stage >= 5 && !thirdPartyInitialStorageBindings.isEmpty()
                && resources.identity() == thirdPartyInitialStorageIdentity) {
            throw new IllegalStateException("Re-enabling the real shaderpack reused the storage-buffer owner released by disable().");
        }
        if (stage >= 5 && !thirdPartyInitialStorageBindings.isEmpty()) {
            thirdPartyStorageBufferOwnerRecreated = true;
        }
        if (stage >= 5 && thirdPartyInitialStorageIdentity != 0) {
            thirdPartyStorageBufferManagerRecreated = true;
        }
        thirdPartyStorageBufferBytes = resources.allocatedBytes();
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "The real shaderpack lifecycle stage {} has storage-buffer bindings {} under owner {} with {} allocated bytes at {}x{}",
                stage, declaredBindings, resources.identity(), resources.allocatedBytes(), resources.viewWidth(), resources.viewHeight());
        return resources;
    }

    /**
     * After a resize settles, make one swapchain recreation observe a 0x0 surface without any later
     * window event. Returns true while the probe still owns the frame loop.
     */
    private static boolean surfaceLossProbeHolding() {
        if (surfaceLossProbeState == 2) return false;
        if (surfaceLossProbeState == 0) {
            SurfaceLossProbe.arm();
            Renderer.scheduleSwapChainUpdate();
            surfaceLossProbeState = 1;
            baselineAt = worldTicks + 40;
            LoggerFactory.getLogger("veyra-graphics-smoke").info("Surface-loss probe armed after resize");
            return true;
        }
        if (!SurfaceLossProbe.applied())
            throw new IllegalStateException("The surface-loss probe did not reach a swapchain recreation");
        surfaceLossProbeState = 2;
        return false;
    }

    private static void requireRecoveredSurface(int frameWidth, int frameHeight) {
        if (surfaceLossProbeState != 2) throw new IllegalStateException("The surface-loss probe did not run before capture");
        var swap = Renderer.getInstance().getSwapChain();
        var depth = Renderer.getInstance().getMainPass().getDepthAttachment();
        if (!swap.hasImages() || swap.getWidth() != frameWidth || swap.getHeight() != frameHeight
                || depth.getWidth(0) != frameWidth || depth.getHeight(0) != frameHeight) {
            throw new IllegalStateException("The swapchain did not recover from a 0x0 surface: hasImages=" + swap.hasImages()
                    + ", swapchain=" + swap.getWidth() + "x" + swap.getHeight() + ", frame=" + frameWidth + "x" + frameHeight
                    + ", mainDepth=" + depth.getWidth(0) + "x" + depth.getHeight(0));
        }
        surfaceLossRecovered = true;
    }

    private static void completeThirdPartyLifecycleCapture(Minecraft client, int frameWidth, int frameHeight,
                                                            boolean active, int nonBlackPixels, int colorBuckets,
                                                            int diagnosticPixels) {
        if (stage == 4) {
            if (active || VeyraRenderer.isRenderingEnabled() || VeyraStorageBufferResources.active() != null) {
                finish(client, new IllegalStateException("The disabled shaderpack frame still owns active shader resources."),
                        diagnosticPixels);
                return;
            }
            thirdPartyDisablePassed = true;
            if (thirdPartyPack == null) {
                finish(client, new IllegalStateException("The shaderpack lifecycle has no pack to re-enable."),
                        diagnosticPixels);
                return;
            }
            String selection = VeyraRenderer.selectWithSavedOptions(thirdPartyPack);
            if (!VeyraRenderer.isRenderingEnabled() || !VeyraRenderer.getLastError().isEmpty()) {
                finish(client, new IllegalStateException("Could not re-select the shaderpack after disable: "
                        + selection + " (" + VeyraRenderer.getLastError() + ")"), diagnosticPixels);
                return;
            }
            applyThirdPartyProfile(thirdPartyPack);
            stage = 5;
            baselineAt = worldTicks + 120;
            pending = false;
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "The shaderpack was re-selected with the same profile after disable.");
            return;
        }

        try {
            if (!active) throw new IllegalStateException("Expected an active shaderpack frame at lifecycle stage " + stage);
            VeyraStorageBufferResources resources = requireThirdPartyActive("frame capture at stage " + stage);
            if (nonBlackPixels < 10000 || (stage == 0 || stage == 6) && colorBuckets < 24) {
                throw new IllegalStateException("The real shaderpack frame failed the visual gate at stage " + stage
                        + ": nonBlack=" + nonBlackPixels + ", colorBuckets=" + colorBuckets);
            }
            thirdPartyNonBlackPixels = nonBlackPixels;
            thirdPartyColorBuckets = colorBuckets;
            activePixels = diagnosticPixels;

            switch (stage) {
                case 0 -> {
                    thirdPartyInitialWidth = frameWidth;
                    thirdPartyInitialHeight = frameHeight;
                    int resizedWidth = frameWidth == 800 ? 960 : 800;
                    int resizedHeight = resizedWidth * 9 / 16;
                    client.getWindow().setWindowed(resizedWidth, resizedHeight);
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Resizing active shaderpack profile from {}x{} to {}x{}",
                            frameWidth, frameHeight, resizedWidth, resizedHeight);
                    stage = 1;
                    baselineAt = worldTicks + 100;
                    pending = false;
                }
                case 1 -> {
                    var colortex = VeyraRenderer.geometryColorAttachment(1);
                    boolean storageBufferViewportMismatch = resources != null
                            && (resources.viewWidth() != thirdPartyInitialWidth && resources.viewWidth() != frameWidth
                            || resources.viewHeight() != thirdPartyInitialHeight && resources.viewHeight() != frameHeight);
                    if (frameWidth == thirdPartyInitialWidth && frameHeight == thirdPartyInitialHeight
                            || colortex.width != frameWidth || colortex.height != frameHeight
                            || storageBufferViewportMismatch) {
                        throw new IllegalStateException("Shaderpack resize resources disagree: initial="
                                + thirdPartyInitialWidth + "x" + thirdPartyInitialHeight + ", frame="
                                + frameWidth + "x" + frameHeight + ", colortex1=" + colortex.width + "x" + colortex.height
                                + ", SSBO viewport=" + (resources == null ? "not declared"
                                : resources.viewWidth() + "x" + resources.viewHeight()));
                    }
                    requireRecoveredSurface(frameWidth, frameHeight);
                    thirdPartyResizePassed = true;
                    client.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0 80 0");
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Shaderpack resize passed at {}x{}; moving the active profile to Nether", frameWidth, frameHeight);
                    stage = 2;
                    baselineAt = worldTicks + 120;
                    pending = false;
                }
                case 2 -> {
                    thirdPartyNetherPassed = true;
                    client.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 "
                            + (thirdPartyNaturalScene ? thirdPartySurfaceHeight + 2 : 72) + " 0");
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Shaderpack Nether profile passed; returning to Overworld");
                    stage = 3;
                    baselineAt = worldTicks + 120;
                    pending = false;
                }
                case 3 -> {
                    thirdPartyReturnPassed = true;
                    thirdPartyPack = VeyraRenderer.selectedPack().orElseThrow(
                            () -> new IllegalStateException("The real shaderpack was not selected before disable."));
                    VeyraStorageBufferResources storageBuffersBeforeDisable = VeyraStorageBufferResources.active();
                    boolean storageBufferOwnerBeforeDisable = storageBuffersBeforeDisable != null
                            && storageBuffersBeforeDisable.allocatedBytes() > 0;
                    String disabled = VeyraRenderer.disable();
                    if (VeyraRenderer.isRenderingEnabled() || VeyraStorageBufferResources.active() != null) {
                        throw new IllegalStateException("Shaderpack disable did not release active GPU resource owners: " + disabled);
                    }
                    thirdPartyStorageBufferOwnerReleased |= storageBufferOwnerBeforeDisable;
                    thirdPartyDisablePassed = true;
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Shaderpack disable completed; storage-buffer allocation before disable was {} bytes",
                            thirdPartyStorageBufferBytes);
                    stage = 4;
                    baselineAt = worldTicks + 80;
                    pending = false;
                }
                case 5 -> {
                    thirdPartyReenablePassed = true;
                    thirdPartyReloadFuture = client.reloadResourcePacks();
                    if (thirdPartyReloadFuture == null) {
                        throw new IllegalStateException("Minecraft did not return a resource-reload future.");
                    }
                    LoggerFactory.getLogger("veyra-graphics-smoke").info(
                            "Started a real client resource reload with the shaderpack active");
                    stage = 6;
                    baselineAt = worldTicks + 100;
                    pending = false;
                }
                case 6 -> {
                    thirdPartyReloadPassed = true;
                    thirdPartyLifecycleCompleted = true;
                    ShutdownEvidence.record("third-party-before-client-stop");
                    finish(client, null, diagnosticPixels);
                }
                default -> throw new IllegalStateException("Unexpected shaderpack lifecycle capture stage: " + stage);
            }
        } catch (Throwable failure) {
            finish(client, failure, diagnosticPixels);
        }
    }

    private static int currentAnimatedPbrFrameSlot(Minecraft client) {
        var texture = client.getTextureManager().getTexture(
                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) return -1;
        var frame = dev.veyra.shader.PbrAnimationClock.animatedSpriteFrames(atlas).get(
                net.minecraft.resources.Identifier.withDefaultNamespace("block/grass_block_top"));
        return frame == null ? -1 : frame.frameSlot();
    }

    private static void captureComparisonMatrices() throws ReflectiveOperationException, java.io.IOException {
        var field = VeyraRenderer.class.getDeclaredField("frameMatrices");
        field.setAccessible(true);
        var frame = field.get(null);
        var matrices = new java.util.LinkedHashMap<String, float[]>();
        for (String name : List.of("gbufferModelView", "gbufferProjection")) {
            var accessor = frame.getClass().getDeclaredMethod(name);
            accessor.setAccessible(true);
            var matrix = (org.joml.Matrix4fc) accessor.invoke(frame);
            matrices.put(name, matrix.get(new float[16]));
        }
        matrices.put("shadowModelView", dev.veyra.shader.VeyraShadowRenderer.shadowModelView().get(new float[16]));
        matrices.put("shadowProjection", dev.veyra.shader.VeyraShadowRenderer.shadowProjection().get(new float[16]));
        Files.createDirectories(output);
        Files.writeString(output.resolve("frame-matrices.json"), new com.google.gson.Gson().toJson(matrices));
        var client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float cameraHeight = SmokeEnvironment.cloudHeight(client, partialTick);
        var uniformsField = VeyraRenderer.class.getDeclaredField("shaderPackFrameUniforms");
        uniformsField.setAccessible(true);
        var uniforms = uniformsField.get(null);
        var cloudHeightAccessor = uniforms.getClass().getDeclaredMethod("cloudHeight");
        cloudHeightAccessor.setAccessible(true);
        float shaderHeight = (float) cloudHeightAccessor.invoke(uniforms);
        if (shaderHeight != cameraHeight) throw new IllegalStateException("Cloud height differs from camera environment");
        Files.writeString(output.resolve("frame-environment.json"), new com.google.gson.Gson().toJson(Map.of(
                "cloudHeight", shaderHeight, "dimensionCloudHeight", SmokeEnvironment.dimensionCloudHeight(client))));
    }

    private static boolean advancePackSwitch(Minecraft client, int magenta, int green) {
        try {
            if (packSwitchPhase == 2) {
                var storage = VeyraStorageBufferResources.active();
                if (magenta < 10000 || storage == null || storage.allocatedBytes() != storageBytesBeforeSwitch)
                    throw new IllegalStateException("Returning to the original pack lost its pixels or storage allocation");
                packSwitchPassed = true;
                return false;
            }
            ShaderPack next;
            if (packSwitchPhase == 0) {
                if (magenta < 10000) throw new IllegalStateException("The original pack has no marker before switching");
                packBeforeSwitch = VeyraRenderer.selectedPack().orElseThrow();
                var storage = VeyraStorageBufferResources.active();
                storageBytesBeforeSwitch = storage == null ? 0 : storage.allocatedBytes();
                var path = Files.createTempDirectory(client.gameDirectory.toPath().resolve("shaderpacks"), "switch-pack-");
                var shaders = Files.createDirectory(path.resolve("shaders"));
                Files.writeString(shaders.resolve("final.fsh"), "#version 120\nuniform sampler2D colortex0;\nvarying vec2 texcoord;\nvoid main(){gl_FragColor=vec4(texture2D(colortex0,texcoord).r*0.001,1,0,1);}\n");
                dev.veyra.VeyraClient.shaderPacks.refresh();
                next = ShaderPack.inspect(path);
            } else {
                if (green < 10000 || magenta > 100) throw new IllegalStateException("The replacement pack did not draw its distinct green frame");
                next = packBeforeSwitch;
            }
            var previousOwner = VeyraStorageBufferResources.active();
            String selected = VeyraRenderer.select(next);
            if (!selected.startsWith("Enabled ") || !VeyraRenderer.getLastError().isEmpty()
                    || !VeyraRenderer.selectedPack().orElseThrow().path().equals(next.path())
                    || previousOwner != null && VeyraStorageBufferResources.active() == previousOwner)
                throw new IllegalStateException("Cross-pack selection did not replace its resource owner: " + selected);
            expectedReplacementError = "";
            packSwitchPhase++;
            baselineAt = worldTicks + 80;
            pending = false;
            return true;
        } catch (Exception failure) {
            finish(client, failure, -1);
            return true;
        }
    }

    private static void verifyFailedReplacement(Minecraft client) throws java.io.IOException {
        var selected = VeyraRenderer.selectedPack().orElseThrow();
        var uniforms = dev.veyra.shader.CustomUniforms.active();
        var storage = VeyraStorageBufferResources.active();
        long bytes = storage == null ? 0 : storage.allocatedBytes();
        var path = Files.createTempDirectory(client.gameDirectory.toPath().resolve("shaderpacks"), "invalid-replacement-");
        var shaders = Files.createDirectory(path.resolve("shaders"));
        Files.writeString(shaders.resolve("shaders.properties"), "uniform.float.rollbackProbe=1\nbufferObject.0=4096\n");
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),
                "#version 120\nvoid main(){gl_FragColor=vec4(veyraRollbackUndefined());}\n");
        String response = VeyraRenderer.select(ShaderPack.inspect(path));
        expectedReplacementError = VeyraRenderer.getLastError();
        if (!response.startsWith("Shader compile failed:") || expectedReplacementError.isEmpty()
                || !VeyraRenderer.isRenderingEnabled() || VeyraRenderer.selectedPack().orElse(null) != selected
                || dev.veyra.shader.CustomUniforms.active() != uniforms
                || VeyraStorageBufferResources.active() != storage
                || storage != null && storage.allocatedBytes() != bytes)
            throw new IllegalStateException("A failed replacement changed the active pack or its resource owners: " + response);
        failedReplacementPassed = true;
        LoggerFactory.getLogger("veyra-graphics-smoke").info(
                "Rejected the invalid replacement and retained the active pack, uniforms and {} storage-buffer bytes; capturing its next frame", bytes);
    }

    private static void finish(Minecraft client, Throwable failure, int baseline) {
        if (finished) return;
        finished = true;
        try {
            Files.createDirectories(output);
            String error = new com.google.gson.Gson().toJson(failure == null ? "" : failure.toString());
            String renderer = physicalDeviceName().replace("\\", "\\\\").replace("\"", "\\\"");
            String profile = thirdPartyProfile.replace("\\", "\\\\").replace("\"", "\\\"");
            String escapedSkipReason = skipReason.replace("\\", "\\\\").replace("\"", "\\\"");
            String status = failure != null ? "failed" : skipReason.isEmpty() ? "passed" : "skipped";
            Files.writeString(output.resolve("result.json"), "{\"status\":\"" + status
                    + "\",\"renderer\":\"" + renderer
                    + "\",\"mouseGrabbed\":" + client.mouseHandler.isMouseGrabbed()
                    + ",\"cursorMode\":" + org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                    + ",\"musicVolume\":" + client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MUSIC)
                    + ",\"failedReplacementPassed\":" + failedReplacementPassed
                    + ",\"packSwitchPassed\":" + packSwitchPassed
                    + ",\"nativeReadbackMagentaPixels\":" + nativeReadbackMagentaPixels
                    + ",\"activeSkyHorizonPixels\":" + activeSkyHorizonPixels
                    + ",\"fixedAnimationClock\":" + Boolean.getBoolean("veyra.reference.fixedClock")
                    + ",\"masterVolume\":" + client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER)
                    + ",\"allSoundVolumesMuted\":" + java.util.Arrays.stream(net.minecraft.sounds.SoundSource.values())
                            .allMatch(category -> client.options.getSoundSourceVolume(category) == 0.0F)
                    + ",\"silentAudioBackend\":" + "null".equals(System.getenv("ALSOFT_DRIVERS"))
                    + ",\"activeMagentaPixels\":" + activePixels
                    + ",\"activeEntityNormalFallbackRedPixels\":" + activeEntityNormalFallbackRedPixels
                    + ",\"disabledEntityNormalFallbackRedPixels\":" + disabledEntityNormalFallbackRedPixels
                    + ",\"animatedPbrFramesChanged\":" + animatedPbrFramesChanged
                    + ",\"animatedPbrFirstRequestedFrameSlot\":" + animatedPbrFirstRequestedFrameSlot
                    + ",\"animatedPbrSecondRequestedFrameSlot\":" + animatedPbrSecondRequestedFrameSlot
                    + ",\"animatedPbrMagentaFrames\":" + (animatedPbrMagentaFirst + animatedPbrMagentaSecond)
                    + ",\"animatedPbrGreenFrames\":" + (animatedPbrGreenFirst + animatedPbrGreenSecond)
                    + ",\"disabledAnimatedPbrMagenta\":" + disabledAnimatedPbrMagenta
                    + ",\"disabledAnimatedPbrGreen\":" + disabledAnimatedPbrGreen
                    + ",\"vertexPipelineStoresAndAtomicsEnabled\":"
                    + VeyraDeviceFeatures.vertexPipelineStoresAndAtomicsEnabled
                    + ",\"shaderStorageImageExtendedFormatsEnabled\":"
                    + VeyraDeviceFeatures.shaderStorageImageExtendedFormatsEnabled
                    + ",\"skipReason\":\"" + escapedSkipReason + "\""
                    + ",\"thirdPartyProfile\":\"" + profile + "\",\"thirdPartyNonBlackPixels\":" + thirdPartyNonBlackPixels
                    + ",\"thirdPartyColorBuckets\":" + thirdPartyColorBuckets
                    + ",\"thirdPartyNaturalScene\":" + thirdPartyNaturalScene
                    + ",\"thirdPartySurfaceHeight\":" + (thirdPartyNaturalScene ? thirdPartySurfaceHeight : 0)
                    + ",\"shadowMrtReenabledPixels\":" + reenabledShadowMrtPixels
                    + ",\"lifecycleInitialWidth\":" + lifecycleInitialWidth
                    + ",\"lifecycleInitialHeight\":" + lifecycleInitialHeight
                    + ",\"setupResizePassed\":" + setupResizePassed
                    + ",\"surfaceLossRecovered\":" + surfaceLossRecovered
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
                    + ",\"activeEntityCustomTextureGreenPixels\":" + activeEntityCustomTextureGreenPixels
                    + ",\"disabledEntityCustomTextureGreenPixels\":" + disabledEntityCustomTextureGreenPixels
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
                    + ",\"thirdPartyLifecycleEnabled\":" + thirdPartyLifecycleFixture
                    + ",\"thirdPartyLifecycleCompleted\":" + thirdPartyLifecycleCompleted
                    + ",\"thirdPartyResizePassed\":" + thirdPartyResizePassed
                    + ",\"thirdPartyNetherPassed\":" + thirdPartyNetherPassed
                    + ",\"thirdPartyReturnPassed\":" + thirdPartyReturnPassed
                    + ",\"thirdPartyDisablePassed\":" + thirdPartyDisablePassed
                    + ",\"thirdPartyReenablePassed\":" + thirdPartyReenablePassed
                    + ",\"thirdPartyReloadPassed\":" + thirdPartyReloadPassed
                    + ",\"thirdPartyStorageBufferBytes\":" + thirdPartyStorageBufferBytes
                    + ",\"thirdPartyStorageBufferExpected\":" + thirdPartyStorageBufferExpected
                    + ",\"thirdPartyStorageBufferOwnerReleased\":" + thirdPartyStorageBufferOwnerReleased
                    + ",\"thirdPartyStorageBufferOwnerRecreated\":" + thirdPartyStorageBufferOwnerRecreated
                    + ",\"thirdPartyStorageBufferManagerRecreated\":" + thirdPartyStorageBufferManagerRecreated
                    + ",\"error\":" + error + "}\n");
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
