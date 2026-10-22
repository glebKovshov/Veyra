package dev.veyra.shader;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Active Iris {@code bufferObject.N} declarations from the selected, preprocessed shader-pack profile. */
public final class ShaderPackStorageBufferProperties {
    private static final String PREFIX = "bufferObject.";

    private final Map<Integer, BufferObject> buffers;

    private ShaderPackStorageBufferProperties(Map<Integer, BufferObject> buffers) {
        this.buffers = Collections.unmodifiableMap(new TreeMap<>(buffers));
    }

    public static ShaderPackStorageBufferProperties parse(String source) throws IOException {
        Properties properties = ShaderPackOptions.loadProperties(source == null ? "" : source);
        Map<Integer, BufferObject> buffers = new TreeMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith(PREFIX)) continue;
            String suffix = key.substring(PREFIX.length());
            int binding;
            try {
                binding = Integer.parseInt(suffix);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid storage-buffer property '" + key + "'. Expected bufferObject.0 through bufferObject.8.", exception);
            }
            if (binding < 0 || binding > 8) {
                throw new IOException("Storage-buffer binding " + binding + " is outside Iris's supported bufferObject.0 through bufferObject.8 range.");
            }

            String[] fields = properties.getProperty(key).strip().split("\\s+");
            if (fields.length != 1 && fields.length != 4) {
                throw new IOException(key + " must contain <byteSize> or <byteSize> <isRelative> <scaleX> <scaleY>.");
            }
            long byteSize;
            try {
                byteSize = Long.parseLong(fields[0]);
            } catch (NumberFormatException exception) {
                throw new IOException(key + " byteSize must be a positive integer.", exception);
            }
            if (byteSize <= 0) throw new IOException(key + " byteSize must be positive.");

            boolean relative = false;
            double scaleX = 1.0;
            double scaleY = 1.0;
            if (fields.length == 4) {
                if (!fields[1].equalsIgnoreCase("true") && !fields[1].equalsIgnoreCase("false")) {
                    throw new IOException(key + " isRelative must be true or false.");
                }
                relative = Boolean.parseBoolean(fields[1]);
                try {
                    scaleX = Double.parseDouble(fields[2]);
                    scaleY = Double.parseDouble(fields[3]);
                } catch (NumberFormatException exception) {
                    throw new IOException(key + " scaleX and scaleY must be finite positive numbers.", exception);
                }
                if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY) || scaleX <= 0.0 || scaleY <= 0.0) {
                    throw new IOException(key + " scaleX and scaleY must be finite positive numbers.");
                }
            }
            buffers.put(binding, new BufferObject(binding, byteSize, relative, scaleX, scaleY));
        }
        return new ShaderPackStorageBufferProperties(buffers);
    }

    public Map<Integer, BufferObject> buffers() {
        return buffers;
    }

    /** Resolves Iris's screen-relative form and checks Veyra/VulkanMod's signed-int descriptor-range limit. */
    public Map<Integer, Long> resolveSizes(int viewWidth, int viewHeight) throws IOException {
        Map<Integer, Long> sizes = new TreeMap<>();
        for (BufferObject buffer : buffers.values()) {
            sizes.put(buffer.binding(), buffer.resolveSize(viewWidth, viewHeight));
        }
        return Collections.unmodifiableMap(sizes);
    }

    public record BufferObject(int binding, long byteSize, boolean relative, double scaleX, double scaleY) {
        public long resolveSize(int viewWidth, int viewHeight) throws IOException {
            long resolved = byteSize;
            if (relative) {
                if (viewWidth <= 0 || viewHeight <= 0) {
                    throw new IOException("Screen-relative bufferObject." + binding + " requires positive view dimensions.");
                }
                double scaledWidth = viewWidth * scaleX;
                double scaledHeight = viewHeight * scaleY;
                if (scaledWidth < 1.0 || scaledHeight < 1.0
                        || scaledWidth > Integer.MAX_VALUE || scaledHeight > Integer.MAX_VALUE) {
                    throw new IOException("Screen-relative bufferObject." + binding + " resolves to invalid dimensions.");
                }
                try {
                    long width = (int) scaledWidth;
                    long height = (int) scaledHeight;
                    resolved = Math.multiplyExact(Math.multiplyExact(width, height), byteSize);
                } catch (ArithmeticException exception) {
                    throw new IOException("Screen-relative bufferObject." + binding + " size overflows.", exception);
                }
            }
            if (resolved > Integer.MAX_VALUE) {
                throw new IOException("bufferObject." + binding + " resolves to " + resolved
                        + " bytes; VulkanMod 0.6.8 stores buffer descriptor ranges as signed integers.");
            }
            return resolved;
        }
    }
}
