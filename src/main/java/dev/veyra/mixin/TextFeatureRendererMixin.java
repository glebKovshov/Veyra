package dev.veyra.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.veyra.shader.VeyraRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private void veyra$drawText(Font font, FormattedCharSequence text, float x, float y, int color,
                                boolean dropShadow, Matrix4f pose, MultiBufferSource bufferSource,
                                Font.DisplayMode displayMode, int backgroundColor, int light,
                                Operation<Void> original,
                                @Local SubmitNodeStorage.TextSubmit textSubmit) {
        VeyraRenderer.beginTextSubmitRender(textSubmit);
        try {
            original.call(font, text, x, y, color, dropShadow, pose, bufferSource,
                    displayMode, backgroundColor, light);
        } finally {
            VeyraRenderer.endTextSubmitRender();
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void veyra$drawOutlinedText(Font font, FormattedCharSequence text, float x, float y,
                                        int color, int outlineColor, Matrix4f pose,
                                        MultiBufferSource bufferSource, int light,
                                        Operation<Void> original,
                                        @Local SubmitNodeStorage.TextSubmit textSubmit) {
        VeyraRenderer.beginTextSubmitRender(textSubmit);
        try {
            original.call(font, text, x, y, color, outlineColor, pose, bufferSource, light);
        } finally {
            VeyraRenderer.endTextSubmitRender();
        }
    }
}
