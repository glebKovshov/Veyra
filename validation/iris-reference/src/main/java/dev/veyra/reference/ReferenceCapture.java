package dev.veyra.reference;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;

/** Reproduces Veyra's third-party shaderpack fixture scene under Iris and captures the initial frame. */
public final class ReferenceCapture implements ClientModInitializer {
    private int worldTicks;
    private boolean prepared;
    private boolean captured;
    private boolean sequenceBusy;
    private int sequenceCaptured;
    private boolean naturalScene;
    private boolean sceneRead;
    private volatile int surfaceHeight = Integer.MIN_VALUE;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (captured) {
            // Optional diagnostic sequence: one screenshot per client tick of the still scene, then stop.
            int sequence = Integer.getInteger("veyra.reference.sequence", 0);
            if (sequence > 0 && !sequenceBusy) {
                if (sequenceCaptured >= sequence) { client.execute(client::stop); sequenceCaptured = Integer.MIN_VALUE; return; }
                if (sequenceCaptured < 0) return;
                sequenceBusy = true;
                int index = sequenceCaptured++;
                File output = new File(System.getProperty("veyra.reference.output", "reference"));
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(new File(output, String.format("sequence-%02d.png", index)));
                    } catch (Exception exception) {
                        throw new IllegalStateException("Could not write the Iris sequence capture", exception);
                    }
                    sequenceBusy = false;
                });
            }
            return;
        }
        if (!sceneRead) {
            sceneRead = true;
            var marker = client.gameDirectory.toPath().resolve("veyra/graphics-fixture.txt");
            try {
                naturalScene = java.nio.file.Files.exists(marker)
                        && java.nio.file.Files.readString(marker).strip().equals("third-party-natural-world");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not read the reference scene", exception);
            }
        }
        if (client.mouseHandler.isMouseGrabbed()
                || org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                != org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL)
            throw new IllegalStateException("Iris validation grabbed the desktop cursor");
        for (var category : net.minecraft.sounds.SoundSource.values()) {
            if (client.options.getSoundSourceVolume(category) != 0.0F)
                client.options.getSoundSourceOptionInstance(category).set(0.0);
        }
        if (client.level == null || client.player == null) return;
        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
        if (client.screen != null) return;
        worldTicks++;
        if (worldTicks == 1) System.out.printf("REFERENCE_INPUT mouseGrabbed=%s cursorMode=%d musicVolume=%.1f masterVolume=%.1f silentAudioBackend=%s%n",
                client.mouseHandler.isMouseGrabbed(),
                org.lwjgl.glfw.GLFW.glfwGetInputMode(client.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR),
                client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MUSIC),
                client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER),
                "null".equals(System.getenv("ALSOFT_DRIVERS")));
        var server = client.getSingleplayerServer();
        if (!prepared && worldTicks >= 20 && server != null) {
            prepared = true;

            server.execute(() -> {
                var world = server.overworld();
                world.setDayTime(1000L);
                world.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
                if (naturalScene) {
                    surfaceHeight = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                    client.execute(() -> {
                        client.player.getAbilities().flying = true;
                        client.player.onUpdateAbilities();
                        client.getConnection().sendCommand("tp @s 0 " + (surfaceHeight + 2) + " 0");
                    });
                    System.out.printf("REFERENCE_NATURAL seed=1 surfaceHeight=%d dayTime=1000%n", surfaceHeight);
                    return;
                }
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                    BlockState block = Math.floorMod(x * 3 + z, 7) == 0 ? Blocks.SANDSTONE.defaultBlockState()
                            : Math.floorMod(x + z * 2, 9) == 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.GRASS_BLOCK.defaultBlockState();
                    world.setBlock(new BlockPos(x, 70, z), block, 3);
                }
                if (Boolean.getBoolean("veyra.reference.waterPool")) {
                    // A pool between the player and the wall, as GraphicsSmoke's waterPool option builds it.
                    for (int x = -6; x <= 6; x++) for (int z = 2; z <= 6; z++) {
                        world.setBlock(new BlockPos(x, 69, z), Blocks.STONE.defaultBlockState(), 3);
                        world.setBlock(new BlockPos(x, 70, z), Blocks.WATER.defaultBlockState(), 3);
                    }
                    System.out.println("REFERENCE_WATER_POOL x=-6..6 z=2..6 y=70");
                    if (Boolean.getBoolean("veyra.reference.poolSeagrass")) {
                        for (int x = -3; x <= 3; x++) for (int z = 4; z <= 5; z++)
                            world.setBlock(new BlockPos(x, 70, z), Blocks.SEAGRASS.defaultBlockState(), 3);
                        System.out.println("REFERENCE_POOL_SEAGRASS x=-3..3 z=4..5 y=70");
                    }
                    if (Boolean.getBoolean("veyra.reference.poolPlants")) {
                        BlockState[] plants = {Blocks.SHORT_GRASS.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                                Blocks.FERN.defaultBlockState(), Blocks.DANDELION.defaultBlockState()};
                        for (int x = -6; x <= 6; x++) {
                            world.setBlock(new BlockPos(x, 70, 7), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                            world.setBlock(new BlockPos(x, 71, 7), plants[Math.floorMod(x, plants.length)], 3);
                        }
                        System.out.println("REFERENCE_POOL_PLANTS x=-6..6 z=7 y=71");
                    }
                }
                BlockState[] materials = {
                        Blocks.OAK_PLANKS.defaultBlockState(), Blocks.BRICKS.defaultBlockState(),
                        Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                        Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.LAPIS_BLOCK.defaultBlockState(),
                        Blocks.RED_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(),
                        Blocks.YELLOW_WOOL.defaultBlockState(), Blocks.LIME_WOOL.defaultBlockState(),
                        Blocks.PURPLE_WOOL.defaultBlockState(), Blocks.CYAN_WOOL.defaultBlockState(),
                        Blocks.DIAMOND_BLOCK.defaultBlockState(), Blocks.AMETHYST_BLOCK.defaultBlockState(),
                        Blocks.PRISMARINE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState()
                };
                for (int row = 0; row < 7; row++) for (int column = 0; column < 17; column++) {
                    world.setBlock(new BlockPos(-8 + column, 71 + row, 8), materials[(row + column) % materials.length], 3);
                }
                if (Boolean.getBoolean("veyra.reference.foliage")) {
                    // Same cutout foliage as GraphicsSmoke's foliage option.
                    BlockState[] foliage = {
                            Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                            Blocks.BIRCH_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                            Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)};
                    for (int x = -5; x <= 5; x++) for (int y = 71; y <= 73; y++)
                        world.setBlock(new BlockPos(x, y, 5), foliage[Math.floorMod(x, 3)], 3);
                    for (int x = -8; x <= 8; x++) world.setBlock(new BlockPos(x, 71, 3), Blocks.SHORT_GRASS.defaultBlockState(), 3);
                }
                world.setBlock(new BlockPos(-9, 71, 8), Blocks.GLOWSTONE.defaultBlockState(), 3);
                world.setBlock(new BlockPos(9, 71, 8), Blocks.GLOWSTONE.defaultBlockState(), 3);
                BlockPos signPos = new BlockPos(0, 71, 8);
                var signState = Blocks.OAK_SIGN.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, 8);
                world.setBlock(signPos, signState, 3);
                if (!(world.getBlockEntity(signPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)
                        || !sign.setText(sign.getFrontText()
                                .setMessage(0, net.minecraft.network.chat.Component.literal("VEYRA")
                                        .withStyle(net.minecraft.ChatFormatting.GREEN))
                                .setMessage(1, net.minecraft.network.chat.Component.literal("SIGN TEST")
                                        .withStyle(net.minecraft.ChatFormatting.GREEN)), true)) {
                    throw new IllegalStateException("Could not prepare the Iris sign-text fixture.");
                }
                world.sendBlockUpdated(signPos, signState, signState, 3);
                client.execute(() -> client.getConnection().sendCommand("tp @s 0 72 0"));
            });
        }
        client.options.hideGui = true;
        client.player.setXRot(naturalScene ? 15.0F : -2.0F);
        client.player.setYRot(naturalScene ? 135.0F : 0.0F);
        if (worldTicks >= 160 + Integer.getInteger("veyra.reference.holdTicks", 0)) {
            if (naturalScene && surfaceHeight == Integer.MIN_VALUE) return;
            var fixtureFloor = new BlockPos(0, naturalScene ? surfaceHeight - 1 : 70, 0);
            var fixtureFloorState = client.level.getBlockState(fixtureFloor);
            var fixtureWall = new BlockPos(0, 71, 8);
            var fixtureWallState = client.level.getBlockState(fixtureWall);
            if (Math.abs(client.player.getX() - 0.5) > 0.25
                    || client.player.getY() < (naturalScene ? surfaceHeight + 1.9 : 70.9)
                    || client.player.getY() > (naturalScene ? surfaceHeight + 2.25 : 72.25)
                    || Math.abs(client.player.getZ() - 0.5) > 0.25 || client.level.getDayTime() != 1000L
                    || fixtureFloorState.isAir() || !naturalScene && fixtureWallState.isAir()) {
                if (worldTicks >= 400) {
                    throw new IllegalStateException("Iris reference capture scene is not ready: pos="
                            + client.player.position() + ", dayTime=" + client.level.getDayTime()
                            + ", floor=" + fixtureFloorState + ", wall=" + fixtureWallState);
                }
                return;
            }
            System.out.printf("IRIS_CAPTURE tick=%d pos=(%.3f,%.3f,%.3f) rotation=(%.3f,%.3f) dayTime=%d floor=%s wall=%s%n",
                    worldTicks, client.player.getX(), client.player.getY(), client.player.getZ(),
                    client.player.getXRot(), client.player.getYRot(), client.level.getDayTime(),
                    fixtureFloorState, client.level.getBlockState(new BlockPos(0, 71, 8)));
            captured = true;
            File output = new File(System.getProperty("veyra.reference.output", "reference"));
            output.mkdirs();
            captureComparisonMatrices(output.toPath());
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try (image) {
                    image.writeToFile(new File(output, "iris-reference-initial.png"));
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not write the Iris reference capture", exception);
                }
                if (Integer.getInteger("veyra.reference.sequence", 0) == 0) client.execute(client::stop);
            });
        }
    }

    private static void captureComparisonMatrices(java.nio.file.Path output) {
        try {
            var stateClass = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            var state = stateClass.getField("INSTANCE").get(null);
            var matrices = new java.util.LinkedHashMap<String, float[]>();
            for (String name : java.util.List.of("GbufferModelView", "GbufferProjection")) {
                var matrix = (org.joml.Matrix4fc) stateClass.getMethod("get" + name).invoke(state);
                matrices.put(Character.toLowerCase(name.charAt(0)) + name.substring(1), matrix.get(new float[16]));
            }
            var shadow = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer");
            matrices.put("shadowModelView", ((org.joml.Matrix4fc) shadow.getField("MODELVIEW").get(null)).get(new float[16]));
            matrices.put("shadowProjection", ((org.joml.Matrix4fc) shadow.getField("PROJECTION").get(null)).get(new float[16]));
            java.nio.file.Files.writeString(output.resolve("frame-matrices.json"), new com.google.gson.Gson().toJson(matrices));
            var client = Minecraft.getInstance();
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            java.nio.file.Files.writeString(output.resolve("frame-environment.json"), new com.google.gson.Gson().toJson(java.util.Map.of(
                    "cloudHeight", client.gameRenderer.getMainCamera().attributeProbe().getValue(
                            net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, partialTick),
                    "dimensionCloudHeight", client.level.environmentAttributes().getDimensionValue(
                            net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT))));
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new IllegalStateException("Could not record the Iris comparison matrices", exception);
        }
    }
}
