package dev.veyra.shader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * An Iris raw custom texture: {@code path TEXTURE_2D|TEXTURE_3D internalFormat width height [depth] pixelFormat
 * pixelType}, whose file holds tightly packed texels. Veyra stores the texels at the precision of the uploaded
 * data, widening three-component data to four components because GPUs rarely sample RGB formats.
 */
public record RawTextureSpec(String path, int dimensions, String internalFormat, int width, int height, int depth,
                             int components, PixelType pixelType) {
    public enum PixelType {
        UNSIGNED_BYTE(1), HALF_FLOAT(2), FLOAT(4);

        final int bytes;

        PixelType(int bytes) { this.bytes = bytes; }
    }

    private static final int MAX_TEXELS = 64 * 1024 * 1024;

    /** Returns null for a plain image path; throws for a malformed or unsupported raw declaration. */
    public static RawTextureSpec parse(String value) throws IOException {
        String[] parts = value.strip().split("\\s+");
        if (parts.length == 1) return null;
        String type = parts[1].toUpperCase(Locale.ROOT);
        int dimensions = switch (type) {
            case "TEXTURE_2D" -> 2;
            case "TEXTURE_3D" -> 3;
            case "TEXTURE_1D", "TEXTURE_RECTANGLE" -> throw new IOException(
                    "Raw shader-pack texture type " + type + " is not supported by Veyra yet: " + value);
            default -> throw new IOException("Unknown raw shader-pack texture type " + type + ": " + value);
        };
        int expected = dimensions == 3 ? 8 : 7;
        if (parts.length != expected) {
            throw new IOException("A " + type + " raw texture needs " + expected + " fields: " + value);
        }
        int width = dimension(parts[3], value);
        int height = dimension(parts[4], value);
        int depth = dimensions == 3 ? dimension(parts[5], value) : 1;
        String pixelFormat = parts[expected - 2].toUpperCase(Locale.ROOT);
        int components = switch (pixelFormat) {
            case "RED" -> 1;
            case "RG" -> 2;
            case "RGB" -> 3;
            case "RGBA" -> 4;
            default -> throw new IOException("Raw texture pixel format " + pixelFormat + " is not supported: " + value);
        };
        PixelType pixelType;
        try {
            pixelType = PixelType.valueOf(parts[expected - 1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unsupported) {
            throw new IOException("Raw texture pixel type " + parts[expected - 1] + " is not supported: " + value);
        }
        if ((long) width * height * depth > MAX_TEXELS) {
            throw new IOException("Raw shader-pack texture is too large: " + value);
        }
        return new RawTextureSpec(parts[0], dimensions, parts[2], width, height, depth, components, pixelType);
    }

    private static int dimension(String field, String value) throws IOException {
        try {
            int parsed = Integer.parseInt(field);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Reported below with the whole declaration.
        }
        throw new IOException("Invalid raw texture dimension '" + field + "': " + value);
    }

    public int sourceBytes() {
        return width * height * depth * components * pixelType.bytes;
    }

    /** Components stored on the GPU: RGB data is widened to RGBA. */
    public int storedComponents() {
        return components == 3 ? 4 : components;
    }

    public int storedBytes() {
        return width * height * depth * storedComponents() * pixelType.bytes;
    }

    /** GLSL sampler type able to read this texture. */
    public String samplerType() {
        return dimensions == 3 ? "sampler3D" : "sampler2D";
    }

    /** The VkFormat holding {@link #storedComponents()} components of {@link #pixelType()}. */
    public int vulkanFormat() {
        int stored = storedComponents();
        return switch (pixelType) {
            case UNSIGNED_BYTE -> stored == 1 ? 9 : stored == 2 ? 16 : 37;      // R8, R8G8, R8G8B8A8 UNORM
            case HALF_FLOAT -> stored == 1 ? 76 : stored == 2 ? 83 : 97;        // R16, R16G16, R16G16B16A16 SFLOAT
            case FLOAT -> stored == 1 ? 100 : stored == 2 ? 103 : 109;          // R32, R32G32, R32G32B32A32 SFLOAT
        };
    }

    /** Validates the file size and widens RGB texels to RGBA with an opaque alpha, into a direct buffer. */
    public ByteBuffer storedTexels(byte[] file) throws IOException {
        if (file.length != sourceBytes()) {
            throw new IOException("Raw shader-pack texture '" + path + "' has " + file.length
                    + " bytes, but its declaration needs " + sourceBytes() + ".");
        }
        // Direct, so the Vulkan upload can copy from its native address.
        ByteBuffer stored = ByteBuffer.allocateDirect(storedBytes()).order(ByteOrder.LITTLE_ENDIAN);
        if (components != 3) return stored.put(file).flip();
        int texelBytes = 3 * pixelType.bytes;
        for (int offset = 0; offset < file.length; offset += texelBytes) {
            stored.put(file, offset, texelBytes);
            switch (pixelType) {
                case UNSIGNED_BYTE -> stored.put((byte) 255);
                case HALF_FLOAT -> stored.putShort((short) 0x3C00);
                case FLOAT -> stored.putFloat(1.0F);
            }
        }
        return stored.flip();
    }
}
