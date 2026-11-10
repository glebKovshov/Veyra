package dev.veyra.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The vanilla render types whose pipelines Veyra can replace on Minecraft 1.21.3. The sky, its
 * stages, weather and particles became render types in 1.21.4; here they bind shader programs
 * directly and are described by program.
 */
public final class VanillaRenderTypes {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private VanillaRenderTypes() { }

    public static List<RenderType> types() {
        return List.of(
                RenderType.armorCutoutNoCull(TEXTURE), RenderType.createArmorDecalCutoutNoCull(TEXTURE),
                RenderType.armorTranslucent(TEXTURE), RenderType.entitySolid(TEXTURE),
                RenderType.entitySolidZOffsetForward(TEXTURE), RenderType.entityCutout(TEXTURE),
                RenderType.entityCutoutNoCull(TEXTURE), RenderType.entityCutoutNoCullZOffset(TEXTURE),
                RenderType.itemEntityTranslucentCull(TEXTURE), RenderType.entityTranslucent(TEXTURE),
                RenderType.entityTranslucentEmissive(TEXTURE), RenderType.entitySmoothCutout(TEXTURE),
                RenderType.beaconBeam(TEXTURE, false), RenderType.beaconBeam(TEXTURE, true),
                RenderType.entityDecal(TEXTURE), RenderType.entityNoOutline(TEXTURE), RenderType.entityShadow(TEXTURE),
                RenderType.dragonExplosionAlpha(TEXTURE), RenderType.eyes(TEXTURE), RenderType.leash(),
                RenderType.armorEntityGlint(), RenderType.glintTranslucent(), RenderType.glint(), RenderType.entityGlint(),
                RenderType.crumbling(TEXTURE), RenderType.text(TEXTURE), RenderType.textBackground(),
                RenderType.textIntensity(TEXTURE), RenderType.textPolygonOffset(TEXTURE),
                RenderType.textIntensityPolygonOffset(TEXTURE), RenderType.textSeeThrough(TEXTURE),
                RenderType.textBackgroundSeeThrough(), RenderType.textIntensitySeeThrough(TEXTURE),
                RenderType.lightning(), RenderType.dragonRays(), RenderType.dragonRaysDepth(),
                RenderType.lines(), RenderType.secondaryBlockOutline(), RenderType.lineStrip());
    }

    /** Describes the program-only sky draws so their pipelines exist for routing. */
    public static void programPipelines() {
        sky();
        stars();
        sunriseSunset();
        celestial();
        endSky();
    }

    public static RenderPipeline sky() {
        return VanillaPipelines.ofProgram("sky", CoreShaders.POSITION, VertexFormat.Mode.TRIANGLE_FAN, false);
    }

    public static RenderPipeline stars() {
        return VanillaPipelines.ofProgram("stars", CoreShaders.POSITION, VertexFormat.Mode.QUADS, true);
    }

    public static RenderPipeline sunriseSunset() {
        return VanillaPipelines.ofProgram("sunrise_sunset", CoreShaders.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, true);
    }

    public static RenderPipeline endSky() {
        return VanillaPipelines.ofProgram("end_sky", CoreShaders.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, true);
    }

    public static RenderPipeline celestial() {
        return VanillaPipelines.ofProgram("celestial", CoreShaders.POSITION_TEX, VertexFormat.Mode.QUADS, true);
    }
}
