package dev.veyra.shader;

/** Tangent-space normal map operations on straight ARGB texture data. */
public final class NormalPixels {
    public static final int FLAT_NORMAL = 0xff7f7fff;

    public record Image(int width, int height, int[] pixels) {
        public Image {
            if (width < 1 || height < 1 || (long) width * height != pixels.length)
                throw new IllegalArgumentException("Invalid normal image dimensions.");
        }
    }

    private NormalPixels() { }

    /** Linearly blend encoded normal vectors, then renormalize the result. */
    public static int interpolate(int from, int to, float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F || amount > 1.0F)
            throw new IllegalArgumentException("Normal interpolation amount must be between zero and one.");
        double[] a = decode(from), b = decode(to);
        double x = a[0] * (1.0 - amount) + b[0] * amount;
        double y = a[1] * (1.0 - amount) + b[1] * amount;
        double z = a[2] * (1.0 - amount) + b[2] * amount;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-8) return FLAT_NORMAL;
        int alpha = Math.clamp(Math.round(channel(from, 24) * (1.0F - amount)
                + channel(to, 24) * amount), 0, 255);
        return alpha << 24 | encode(x / length) << 16 | encode(y / length) << 8 | encode(z / length);
    }

    public static String path(String base, boolean atlasSprite) {
        if (atlasSprite) return (base.startsWith("optifine/cit/") ? "" : "textures/") + base + "_n.png";
        int extension = base.lastIndexOf('.');
        return extension < 0 ? base + "_n" : base.substring(0, extension) + "_n" + base.substring(extension);
    }

    public static Image flat(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid normal image dimensions.");
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, FLAT_NORMAL);
        return new Image(width, height, pixels);
    }

    /** Resample a normal sprite into its padded atlas rectangle and extend edge normals. */
    public static void tile(Image atlas, Image source, int x, int y, int width, int height, int padding) {
        if (width < 1 || height < 1 || padding < 0 || x < 0 || y < 0
                || x + (long) width + 2L * padding > atlas.width
                || y + (long) height + 2L * padding > atlas.height)
            throw new IllegalArgumentException("Normal sprite lies outside the base atlas.");
        boolean nearest = width % source.width == 0 && height % source.height == 0;
        for (int dy = -padding; dy < height + padding; dy++) {
            for (int dx = -padding; dx < width + padding; dx++) {
                int tx = Math.clamp(dx, 0, width - 1);
                int ty = Math.clamp(dy, 0, height - 1);
                int color;
                if (nearest) {
                    int sx = Math.min(source.width - 1, (int) ((long) tx * source.width / width));
                    int sy = Math.min(source.height - 1, (int) ((long) ty * source.height / height));
                    color = source.pixels[sy * source.width + sx];
                } else {
                    double sx = (tx + 0.5) * source.width / width - 0.5;
                    double sy = (ty + 0.5) * source.height / height - 0.5;
                    color = sample(source, sx, sy);
                }
                atlas.pixels[(y + dy + padding) * atlas.width + x + dx + padding] = color;
            }
        }
    }

    /** Average vectors and renormalize; averaging encoded RGB values darkens normal maps. */
    public static Image mip(Image source) {
        int width = Math.max(1, source.width / 2);
        int height = Math.max(1, source.height / 2);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = at(source, x * 2, y * 2);
                int b = at(source, x * 2 + 1, y * 2);
                int c = at(source, x * 2, y * 2 + 1);
                int d = at(source, x * 2 + 1, y * 2 + 1);
                pixels[y * width + x] = a == b && a == c && a == d
                        ? a : averageNormals(a, b, c, d);
            }
        }
        return new Image(width, height, pixels);
    }

    private static int sample(Image image, double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double u = x - ix;
        double v = y - iy;
        int a = at(image, ix, iy);
        int b = at(image, ix + 1, iy);
        int c = at(image, ix, iy + 1);
        int d = at(image, ix + 1, iy + 1);
        return blendNormals(a, b, c, d, u, v);
    }

    private static int averageNormals(int a, int b, int c, int d) {
        return blendNormals(a, b, c, d, 0.5, 0.5);
    }

    private static int blendNormals(int a, int b, int c, int d, double u, double v) {
        double[] na = decode(a), nb = decode(b), nc = decode(c), nd = decode(d);
        double xTop = na[0] * (1 - u) + nb[0] * u;
        double xBottom = nc[0] * (1 - u) + nd[0] * u;
        double yTop = na[1] * (1 - u) + nb[1] * u;
        double yBottom = nc[1] * (1 - u) + nd[1] * u;
        double zTop = na[2] * (1 - u) + nb[2] * u;
        double zBottom = nc[2] * (1 - u) + nd[2] * u;
        double nx = xTop * (1 - v) + xBottom * v;
        double ny = yTop * (1 - v) + yBottom * v;
        double nz = zTop * (1 - v) + zBottom * v;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-8) return FLAT_NORMAL;
        nx /= length;
        ny /= length;
        nz /= length;
        int red = encode(nx);
        int green = encode(ny);
        int blue = encode(nz);
        int alpha = (int) Math.round(channel(a, 24) * (1 - u) * (1 - v)
                + channel(b, 24) * u * (1 - v)
                + channel(c, 24) * (1 - u) * v
                + channel(d, 24) * u * v);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static double[] decode(int color) {
        return new double[]{channel(color, 16) / 127.5 - 1.0,
                channel(color, 8) / 127.5 - 1.0,
                channel(color, 0) / 127.5 - 1.0};
    }

    private static int encode(double value) {
        return Math.clamp((int) Math.round((value * 0.5 + 0.5) * 255.0), 0, 255);
    }

    private static int channel(int color, int shift) { return color >>> shift & 255; }
    private static int at(Image image, int x, int y) {
        return image.pixels[Math.clamp(y, 0, image.height - 1) * image.width + Math.clamp(x, 0, image.width - 1)];
    }
}
