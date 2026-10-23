package dev.veyra.shader;

/** CPU material data operations. Colors are straight ARGB data, never sRGB or premultiplied. */
public final class SpecularPixels {
    public record Image(int width, int height, int[] pixels) {
        public Image {
            if (width < 1 || height < 1 || (long) width * height != pixels.length)
                throw new IllegalArgumentException("Invalid material image dimensions.");
        }
    }
    private SpecularPixels() { }

    /** Blend straight material-data channels without color-space conversion. */
    public static int interpolate(int from, int to, float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F || amount > 1.0F)
            throw new IllegalArgumentException("Specular interpolation amount must be between zero and one.");
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int value = Math.round(channel(from, shift) * (1.0F - amount) + channel(to, shift) * amount);
            result |= value << shift;
        }
        return result;
    }

    public static String path(String base, boolean atlasSprite) {
        if (atlasSprite) return (base.startsWith("optifine/cit/") ? "" : "textures/") + base + "_s.png";
        int extension = base.lastIndexOf('.');
        return extension < 0 ? base + "_s" : base.substring(0, extension) + "_s" + base.substring(extension);
    }

    /** Resample a sprite into its existing padded atlas rectangle, extending edge texels. */
    public static void tile(Image atlas, Image source, int x, int y, int width, int height, int padding) {
        if (width < 1 || height < 1 || padding < 0 || x < 0 || y < 0
                || x + (long) width + 2L * padding > atlas.width || y + (long) height + 2L * padding > atlas.height)
            throw new IllegalArgumentException("Material sprite lies outside the base atlas.");
        boolean nearest = width % source.width == 0 && height % source.height == 0;
        for (int dy = -padding; dy < height + padding; dy++) for (int dx = -padding; dx < width + padding; dx++) {
            int tx = Math.clamp(dx, 0, width - 1), ty = Math.clamp(dy, 0, height - 1);
            int color;
            if (nearest) color = source.pixels[(int) ((long) ty * source.height / height) * source.width + (int) ((long) tx * source.width / width)];
            else {
                double sx = (tx + 0.5) * source.width / width - 0.5, sy = (ty + 0.5) * source.height / height - 0.5;
                int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
                int a = at(source, ix, iy), b = at(source, ix + 1, iy), c = at(source, ix, iy + 1), d = at(source, ix + 1, iy + 1);
                color = 0;
                for (int shift = 0; shift <= 24; shift += 8) {
                    double u = sx - ix, v = sy - iy;
                    double top = channel(a, shift) * (1 - u) + channel(b, shift) * u;
                    double bottom = channel(c, shift) * (1 - u) + channel(d, shift) * u;
                    color |= (int) Math.round(top * (1 - v) + bottom * v) << shift;
                }
            }
            atlas.pixels[(y + dy + padding) * atlas.width + x + dx + padding] = color;
        }
    }

    public static Image mip(Image source, boolean labPbr) {
        int width = Math.max(1, source.width / 2), height = Math.max(1, source.height / 2);
        int[] pixels = new int[width * height];
        int[] values = new int[4];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            values[0] = at(source, x * 2, y * 2); values[1] = at(source, x * 2 + 1, y * 2);
            values[2] = at(source, x * 2, y * 2 + 1); values[3] = at(source, x * 2 + 1, y * 2 + 1);
            int color = 0;
            for (int shift = 0; shift <= 24; shift += 8) {
                int best = 0, votes = 0;
                if (labPbr && shift != 16) for (int value : values) {
                    int group = category(channel(value, shift), shift), count = 0;
                    for (int other : values) if (category(channel(other, shift), shift) == group) count++;
                    if (count > votes) { votes = count; best = group; }
                }
                int sum = 0, count = 0;
                for (int value : values) if (!labPbr || shift == 16 || category(channel(value, shift), shift) == best) {
                    sum += channel(value, shift); count++;
                }
                color |= (sum / count) << shift;
            }
            pixels[y * width + x] = color;
        }
        return new Image(width, height, pixels);
    }

    private static int category(int value, int shift) {
        return switch (shift) {
            case 8 -> value < 230 ? 0 : value - 229; // metal identity vs dielectric F0
            case 0 -> value < 65 ? 0 : 1; // porosity vs subsurface scattering
            case 24 -> value < 255 ? 0 : 1; // emissive vs non-emissive sentinel
            default -> 0;
        };
    }
    private static int channel(int color, int shift) { return color >>> shift & 255; }
    private static int at(Image image, int x, int y) {
        return image.pixels[Math.clamp(y, 0, image.height - 1) * image.width + Math.clamp(x, 0, image.width - 1)];
    }
}
