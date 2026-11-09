package dev.veyra.shader;

import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;

/** End flashes were introduced after 1.21.5. */
final class VeyraEndFlash {
    private VeyraEndFlash() { }

    static float[] position(ClientLevel level, Matrix4f modelView) { return new float[3]; }
    static float intensity(ClientLevel level, float partialTick) { return 0.0F; }
}
