package dev.veyra.shader;

import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class VeyraEndFlash {
    private VeyraEndFlash() { }

    static float[] position(ClientLevel level, Matrix4f modelView) {
        var flash = level.endFlashState();
        if (flash == null) return new float[3];
        Vector4f position = new Matrix4f(modelView)
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(180.0F - flash.getYAngle()))
                .rotate(com.mojang.math.Axis.XP.rotationDegrees(-90.0F - flash.getXAngle()))
                .transform(new Vector4f(0.0F, 100.0F, 0.0F, 0.0F));
        return new float[]{position.x, position.y, position.z};
    }

    static float intensity(ClientLevel level, float partialTick) {
        var flash = level.endFlashState();
        return flash == null ? 0.0F : flash.getIntensity(partialTick);
    }
}
