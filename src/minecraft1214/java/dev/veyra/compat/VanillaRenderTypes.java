package dev.veyra.compat;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The vanilla render types whose pipelines Veyra can replace on Minecraft 1.21.4. */
final class VanillaRenderTypes {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private VanillaRenderTypes() { }

    static List<RenderType> types() {
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
                RenderType.lines(), RenderType.secondaryBlockOutline(), RenderType.lineStrip(),
                RenderType.worldBorder(false), RenderType.worldBorder(true),
                RenderType.opaqueParticle(TEXTURE), RenderType.translucentParticle(TEXTURE),
                RenderType.weather(TEXTURE, false), RenderType.weather(TEXTURE, true),
                RenderType.sky(), RenderType.endSky(), RenderType.sunriseSunset(), RenderType.stars(),
                RenderType.celestial(TEXTURE));
    }

    /** Draws without a render type; 1.21.4 has none that Veyra replaces. */
    static void programPipelines() { }

    /** The sky disc pipeline; resolved on use because program configs change with resource reloads. */
    static RenderPipeline sky() { return VanillaPipelines.of(RenderType.sky()); }
}
