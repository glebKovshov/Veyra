package dev.veyra.mixin.accessor;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("textures")
    Map<String, ?> veyra$getTextures();

    @Accessor("useLightmap")
    boolean veyra$getUseLightmap();

    @Accessor("useOverlay")
    boolean veyra$getUseOverlay();

    @Accessor("layeringTransform")
    LayeringTransform veyra$getLayeringTransform();

    @Accessor("outputTarget")
    OutputTarget veyra$getOutputTarget();

    @Accessor("textureTransform")
    TextureTransform veyra$getTextureTransform();

    @Accessor("outlineProperty")
    RenderSetup.OutlineProperty veyra$getOutlineProperty();

    @Accessor("affectsCrumbling")
    boolean veyra$getAffectsCrumbling();

    @Accessor("sortOnUpload")
    boolean veyra$getSortOnUpload();

    @Accessor("bufferSize")
    int veyra$getBufferSize();

    @Invoker("<init>")
    static RenderSetup veyra$create(RenderPipeline pipeline, Map<String, ?> textures,
                                    boolean useLightmap, boolean useOverlay,
                                    LayeringTransform layeringTransform, OutputTarget outputTarget,
                                    TextureTransform textureTransform, RenderSetup.OutlineProperty outlineProperty,
                                    boolean affectsCrumbling, boolean sortOnUpload, int bufferSize) {
        throw new AssertionError();
    }
}
