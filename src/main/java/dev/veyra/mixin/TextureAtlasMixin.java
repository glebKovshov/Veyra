package dev.veyra.mixin;
import dev.veyra.vulkan.VeyraSpecularTextures;
import dev.veyra.vulkan.VeyraNormalTextures;
import dev.veyra.shader.PbrAnimationClock;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.SpriteLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @Inject(method = "upload", at = @At("HEAD"))
    private void veyra$invalidateMaterialInputs(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        VeyraSpecularTextures.invalidate();
        VeyraNormalTextures.invalidate();
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void veyra$captureBlockAtlasSize(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (((TextureAtlas) (Object) this).location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            dev.veyra.shader.VeyraRenderer.captureBlockAtlasSize(preparations.width(), preparations.height());
        }
    }

    @Inject(method = "cycleAnimationFrames", at = @At("TAIL"))
    private void veyra$tickAnimatedMaterialMaps(CallbackInfo ci) {
        TextureAtlas atlas = (TextureAtlas) (Object) this;
        if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) return;
        long tick = PbrAnimationClock.advance();
        var spriteFrames = PbrAnimationClock.animatedSpriteFrames(atlas);
        VeyraNormalTextures.tickAnimations(tick, spriteFrames);
        VeyraSpecularTextures.tickAnimations(tick, spriteFrames);
    }
}
