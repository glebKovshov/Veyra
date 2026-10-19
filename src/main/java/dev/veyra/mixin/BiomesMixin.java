package dev.veyra.mixin;

import dev.veyra.shader.BiomeIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Numbers vanilla biomes in registration order, matching Iris' biome uniform ids. */
@Mixin(Biomes.class)
public abstract class BiomesMixin {
    @Inject(method = "register", at = @At("TAIL"))
    private static void veyra$recordBiomeId(String name, CallbackInfoReturnable<ResourceKey<Biome>> callback) {
        BiomeIds.register(callback.getReturnValue().identifier().toString());
    }
}
