package dev.veyra.shader;

import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A bounded CPU view of Minecraft's vertical or grid-form animated texture sheets. */
public final class PbrAnimation {
    private static final int MAX_FRAME_COUNT = 16_384;

    @FunctionalInterface
    public interface PixelBlend {
        int blend(int from, int to, float amount);
    }

    public record Position(int frameSlot, int frameIndex, int nextFrameIndex,
                           int elapsedTicks, int duration, boolean interpolate) {
        public float blendAmount() {
            return interpolate ? (float) elapsedTicks / duration : 0.0F;
        }

        public boolean visuallyDiffersFrom(Position other) {
            if (other == null || frameIndex != other.frameIndex || nextFrameIndex != other.nextFrameIndex
                    || interpolate != other.interpolate) return true;
            return interpolate && elapsedTicks != other.elapsedTicks;
        }
    }

    private record Frame(int index, int duration) { }

    private final int imageWidth;
    private final int imageHeight;
    private final int frameWidth;
    private final int frameHeight;
    private final int columns;
    private final int[] pixels;
    private final List<Frame> frames;
    private final long cycleTicks;
    private final boolean interpolate;

    private PbrAnimation(int imageWidth, int imageHeight, int frameWidth, int frameHeight,
                         int[] pixels, List<Frame> frames, boolean interpolate) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.columns = imageWidth / frameWidth;
        this.pixels = pixels;
        this.frames = List.copyOf(frames);
        this.cycleTicks = frames.stream().mapToLong(Frame::duration).sum();
        this.interpolate = interpolate;
    }

    public static PbrAnimation fromSheet(int imageWidth, int imageHeight, int[] pixels,
                                         AnimationMetadataSection metadata) throws IOException {
        if (imageWidth < 1 || imageHeight < 1 || (long) imageWidth * imageHeight != pixels.length)
            throw new IOException("Animated PBR map has invalid image dimensions.");
        FrameSize size = metadata.calculateFrameSize(imageWidth, imageHeight);
        int frameWidth = size.width();
        int frameHeight = size.height();
        if (frameWidth < 1 || frameHeight < 1 || imageWidth % frameWidth != 0 || imageHeight % frameHeight != 0)
            throw new IOException("Animated PBR map dimensions are not divisible by its frame size.");

        int frameCount = Math.multiplyExact(imageWidth / frameWidth, imageHeight / frameHeight);
        if (frameCount > MAX_FRAME_COUNT)
            throw new IOException("Animated PBR map has more than " + MAX_FRAME_COUNT + " frames.");
        List<AnimationFrame> declared = metadata.frames().orElse(List.of());
        if (declared.size() > MAX_FRAME_COUNT)
            throw new IOException("Animated PBR map has more than " + MAX_FRAME_COUNT + " animation entries.");
        List<Frame> frames = new ArrayList<>();
        if (declared.isEmpty()) {
            for (int index = 0; index < frameCount; index++)
                frames.add(new Frame(index, metadata.defaultFrameTime()));
        } else {
            for (AnimationFrame frame : declared) {
                int duration = frame.timeOr(metadata.defaultFrameTime());
                if (frame.index() < 0 || frame.index() >= frameCount)
                    throw new IOException("Animated PBR map frame index " + frame.index()
                            + " is outside its " + frameCount + " frames.");
                if (duration < 1)
                    throw new IOException("Animated PBR map frame duration must be positive.");
                frames.add(new Frame(frame.index(), duration));
            }
        }
        if (frames.isEmpty()) throw new IOException("Animated PBR map has no frames.");
        for (Frame frame : frames) if (frame.duration() < 1)
            throw new IOException("Animated PBR map frame duration must be positive.");
        long cycleTicks = 0;
        try {
            for (Frame frame : frames) cycleTicks = Math.addExact(cycleTicks, frame.duration());
        } catch (ArithmeticException exception) {
            throw new IOException("Animated PBR map frame durations overflow.", exception);
        }
        if (cycleTicks < 1) throw new IOException("Animated PBR map has an empty animation cycle.");
        return new PbrAnimation(imageWidth, imageHeight, frameWidth, frameHeight,
                pixels, frames, metadata.interpolatedFrames());
    }

    public int frameWidth() { return frameWidth; }
    public int frameHeight() { return frameHeight; }
    public int pixelCount() { return pixels.length; }
    public boolean isAnimated() {
        if (frames.size() < 2) return false;
        int first = frames.getFirst().index();
        return frames.stream().anyMatch(frame -> frame.index() != first);
    }

    public int[] firstFramePixels() {
        int[] result = new int[Math.multiplyExact(frameWidth, frameHeight)];
        copyPixels(positionAt(0), result, (from, to, amount) -> to);
        return result;
    }

    public Position positionAt(long elapsedTicks) {
        long time = Math.floorMod(elapsedTicks, cycleTicks);
        int frameSlot = 0;
        while (frameSlot + 1 < frames.size() && time >= frames.get(frameSlot).duration()) {
            time -= frames.get(frameSlot).duration();
            frameSlot++;
        }
        Frame current = frames.get(frameSlot);
        Frame next = frames.get((frameSlot + 1) % frames.size());
        return new Position(frameSlot, current.index(), next.index(), (int) time,
                current.duration(), interpolate);
    }

    /** Selects the matching animation entry used by the base atlas sprite. */
    public Position positionForFrameSlot(int frameSlot, int elapsedTicks) {
        int slot = Math.floorMod(frameSlot, frames.size());
        Frame current = frames.get(slot);
        Frame next = frames.get((slot + 1) % frames.size());
        int frameElapsed = Math.floorMod(elapsedTicks, current.duration());
        return new Position(slot, current.index(), next.index(), frameElapsed, current.duration(), interpolate);
    }

    /** Writes a selected frame into a reusable ARGB target buffer. */
    public void copyPixels(Position position, int[] target, PixelBlend blender) {
        if (target.length != frameWidth * frameHeight)
            throw new IllegalArgumentException("PBR animation target does not match its frame dimensions.");
        int currentX = position.frameIndex() % columns * frameWidth;
        int currentY = position.frameIndex() / columns * frameHeight;
        int nextX = position.nextFrameIndex() % columns * frameWidth;
        int nextY = position.nextFrameIndex() / columns * frameHeight;
        float blend = position.blendAmount();
        for (int y = 0; y < frameHeight; y++) {
            int current = (currentY + y) * imageWidth + currentX;
            int next = (nextY + y) * imageWidth + nextX;
            int output = y * frameWidth;
            for (int x = 0; x < frameWidth; x++) {
                int from = pixels[current + x];
                target[output + x] = blend == 0.0F ? from : blender.blend(from, pixels[next + x], blend);
            }
        }
    }
}
