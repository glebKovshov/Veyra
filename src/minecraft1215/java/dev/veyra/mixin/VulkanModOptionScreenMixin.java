package dev.veyra.mixin;

import dev.veyra.screen.ShaderPackScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.VOptionScreen;
import net.vulkanmod.config.gui.widget.VButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Adds "Shader Settings" to VulkanMod's video settings, beside its Apply/Done row (as Iris does for Sodium). */
@Mixin(value = VOptionScreen.class, remap = false)
abstract class VulkanModOptionScreenMixin extends Screen {
    @Shadow(remap = false) private VButtonWidget applyButton;
    @Shadow(remap = false) @org.spongepowered.asm.mixin.Final private List<VButtonWidget> buttons;

    protected VulkanModOptionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "addButtons", at = @At("TAIL"), remap = false)
    private void veyra$addShaderSettingsButton(CallbackInfo callback) {
        if (applyButton == null) return;
        Component label = Component.translatable("veyra.options.shader_settings");
        int buttonWidth = font.width(label) + 20;
        VButtonWidget button = new VButtonWidget(applyButton.getX() - buttonWidth - 5, applyButton.getY(), buttonWidth, 20,
                label, ignored -> minecraft.setScreen(new ShaderPackScreen(this)));
        buttons.add(button);
        addWidget(button);
    }
}
