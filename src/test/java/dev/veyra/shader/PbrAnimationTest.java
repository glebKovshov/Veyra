package dev.veyra.shader;

import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PbrAnimationTest {
    private static AnimationFrame frame(int index, int duration) {
        return new AnimationFrame(index, Optional.of(duration));
    }

    private static AnimationMetadataSection metadata(List<AnimationFrame> frames, int width, int height,
                                                       int defaultTime, boolean interpolate) {
        return new AnimationMetadataSection(Optional.ofNullable(frames), Optional.of(width), Optional.of(height),
                defaultTime, interpolate);
    }

    @Test
    void followsDeclaredGridFramesAndTheirIndividualDurations() throws IOException {
        int[] sheet = {
                0xff000001, 0xff000002, 0xff000003, 0xff000004,
                0xff000005, 0xff000006, 0xff000007, 0xff000008
        };
        PbrAnimation animation = PbrAnimation.fromSheet(4, 2, sheet,
                metadata(List.of(frame(1, 2), frame(0, 3)), 2, 2, 1, false));

        assertTrue(animation.isAnimated());
        assertEquals(1, animation.positionAt(0).frameIndex());
        assertEquals(1, animation.positionAt(1).frameIndex());
        assertEquals(0, animation.positionAt(2).frameIndex());
        assertEquals(0, animation.positionAt(4).frameIndex());
        assertEquals(1, animation.positionAt(5).frameIndex());
        assertEquals(0xff000003, animation.firstFramePixels()[0]);
    }

    @Test
    void usesSequentialFramesWhenMetadataDoesNotDeclareAFrameList() throws IOException {
        PbrAnimation animation = PbrAnimation.fromSheet(1, 3,
                new int[]{0xff000001, 0xff000002, 0xff000003},
                metadata(null, 1, 1, 2, false));

        assertEquals(0, animation.positionAt(0).frameIndex());
        assertEquals(0, animation.positionAt(1).frameIndex());
        assertEquals(1, animation.positionAt(2).frameIndex());
        assertEquals(2, animation.positionAt(4).frameIndex());
    }

    @Test
    void followsTheBaseSpriteAnimationEntryAndSubFrame() throws IOException {
        PbrAnimation animation = PbrAnimation.fromSheet(1, 2,
                new int[]{0xff000001, 0xff000002},
                metadata(List.of(frame(1, 4), frame(0, 6)), 1, 1, 1, false));

        PbrAnimation.Position first = animation.positionForFrameSlot(0, 2);
        PbrAnimation.Position second = animation.positionForFrameSlot(1, 8);

        assertEquals(1, first.frameIndex());
        assertEquals(0, first.nextFrameIndex());
        assertEquals(2, first.elapsedTicks());
        assertEquals(0, second.frameIndex());
        assertEquals(1, second.nextFrameIndex());
        assertEquals(2, second.elapsedTicks());
    }

    @Test
    void interpolatesBetweenTheCurrentAndNextFrameOnlyWhenEnabled() throws IOException {
        PbrAnimation animation = PbrAnimation.fromSheet(1, 2,
                new int[]{0xff8080ff, 0xffff80ff},
                metadata(List.of(frame(0, 4), frame(1, 4)), 1, 1, 1, true));
        PbrAnimation.Position midpoint = animation.positionAt(2);
        int[] pixels = new int[1];

        animation.copyPixels(midpoint, pixels, NormalPixels::interpolate);

        int blended = pixels[0];
        assertTrue((blended >>> 16 & 255) > 170, Integer.toHexString(blended));
        assertTrue((blended & 255) > 220, Integer.toHexString(blended));
        assertEquals(255, blended >>> 24);
        assertTrue(midpoint.visuallyDiffersFrom(animation.positionAt(1)));
        assertFalse(animation.positionAt(0).visuallyDiffersFrom(animation.positionAt(8)));
    }

    @Test
    void rejectsMalformedFrameGeometryAndMetadata() {
        assertThrows(IOException.class, () -> PbrAnimation.fromSheet(3, 2, new int[6],
                metadata(null, 2, 2, 1, false)));
        assertThrows(IOException.class, () -> PbrAnimation.fromSheet(2, 2, new int[4],
                metadata(List.of(frame(1, 1)), 2, 2, 1, false)));
        assertThrows(IOException.class, () -> PbrAnimation.fromSheet(1, 1, new int[1],
                metadata(List.of(frame(0, 0)), 1, 1, 1, false)));
    }

    @Test
    void blendsNormalVectorsAndStraightSpecularChannels() {
        int normal = NormalPixels.interpolate(0xff8080ff, 0xffff80ff, 0.5F);
        int specular = SpecularPixels.interpolate(0x10204080, 0xf0e0c080, 0.5F);

        assertTrue((normal >>> 16 & 255) > 170, Integer.toHexString(normal));
        assertTrue((normal & 255) > 220, Integer.toHexString(normal));
        assertEquals(0x80808080, specular);
        assertThrows(IllegalArgumentException.class, () -> NormalPixels.interpolate(0, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> SpecularPixels.interpolate(0, 0, 1.5F));
    }
}
