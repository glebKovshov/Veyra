package dev.veyra.shader;

import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed Iris {@code image.*} declarations from the active, preprocessed shaders.properties. */
public record ShaderPackImageProperties(Map<String, Image> images, Map<String, String> macros) {
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([^\\r\\n]+)");
    private static final Pattern DIMENSION = Pattern.compile("[+-]?\\d+|[A-Za-z_][A-Za-z0-9_]*");

    public ShaderPackImageProperties {
        images = Collections.unmodifiableMap(new LinkedHashMap<>(images));
        macros = Map.copyOf(macros);
    }

    public static ShaderPackImageProperties parse(String source) throws IOException {
        Properties properties = ShaderPackOptions.loadProperties(source);
        Map<String, String> macros = new LinkedHashMap<>();
        Matcher defines = DEFINE.matcher(source);
        while (defines.find()) {
            String value = defines.group(2).strip().replaceFirst("\\s+//.*$", "").strip();
            if (value.matches("[A-Za-z_][A-Za-z0-9_]*|[+-]?\\d+")) {
                macros.put(defines.group(1), value);
            }
        }

        Map<String, Image> images = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith("image.")) continue;
            String name = key.substring("image.".length());
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IOException("Invalid shader-pack image name: " + key);
            }
            String[] fields = properties.getProperty(key).strip().split("\\s+");
            if (fields.length != 8 && fields.length != 9) {
                throw new IOException("Invalid " + key + " declaration: expected sampler, format, type, flags, and 2D or 3D dimensions.");
            }
            String sampler = fields[0];
            if (!sampler.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IOException("Invalid sampler name in " + key + ": " + sampler);
            }
            boolean clear = parseBoolean(key, "clear", fields[4]);
            boolean mipmap = parseBoolean(key, "mipmap", fields[5]);
            List<String> dimensions = new ArrayList<>(fields.length - 6);
            for (int i = 6; i < fields.length; i++) {
                if (!DIMENSION.matcher(fields[i]).matches()) {
                    throw new IOException("Invalid dimension token in " + key + ": " + fields[i]);
                }
                dimensions.add(fields[i]);
            }
            images.put(name, new Image(name, sampler, fields[1], fields[2], fields[3], clear, mipmap, dimensions));
        }
        return new ShaderPackImageProperties(images, macros);
    }

    private static boolean parseBoolean(String key, String field, String value) throws IOException {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IOException("Invalid " + field + " flag in image property " + key + ": " + value);
    }

    public int[] resolveDimensions(Image image) throws IOException {
        int[] dimensions = new int[image.dimensionTokens().size()];
        for (int i = 0; i < dimensions.length; i++) {
            dimensions[i] = resolveDimension(image.name(), image.dimensionTokens().get(i), new java.util.HashSet<>());
            if (dimensions[i] <= 0) {
                throw new IOException("Image " + image.name() + " has a non-positive dimension.");
            }
        }
        return dimensions;
    }

    private int resolveDimension(String image, String token, java.util.Set<String> visited) throws IOException {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException ignored) {
            // Resolve a simple integer macro; reject expressions instead of guessing their value.
        }
        if (!visited.add(token)) throw new IOException("Cyclic dimension macro in image " + image + ": " + token);
        String value = macros.get(token);
        if (value == null) throw new IOException("Image " + image + " uses unresolved dimension macro " + token + ".");
        return resolveDimension(image, value, visited);
    }

    public record Image(String name, String sampler, String channels, String internalFormat, String dataType,
                        boolean clear, boolean mipmap, List<String> dimensionTokens) {
        public Image {
            dimensionTokens = List.copyOf(dimensionTokens);
            if (dimensionTokens.size() != 2 && dimensionTokens.size() != 3) {
                throw new IllegalArgumentException("Shader-pack images must have two or three dimensions.");
            }
        }

        public boolean is3d() {
            return dimensionTokens.size() == 3;
        }

        public int vulkanFormat() throws IOException {
            return switch (internalFormat.toLowerCase(java.util.Locale.ROOT)) {
                case "r8ui" -> VK10.VK_FORMAT_R8_UINT;
                case "r16ui" -> VK10.VK_FORMAT_R16_UINT;
                case "r32ui" -> VK10.VK_FORMAT_R32_UINT;
                case "r8" -> VK10.VK_FORMAT_R8_UNORM;
                case "r16f" -> VK10.VK_FORMAT_R16_SFLOAT;
                case "r32f" -> VK10.VK_FORMAT_R32_SFLOAT;
                case "rg8" -> VK10.VK_FORMAT_R8G8_UNORM;
                case "rg16f" -> VK10.VK_FORMAT_R16G16_SFLOAT;
                case "rg32f" -> VK10.VK_FORMAT_R32G32_SFLOAT;
                case "rgba8" -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
                case "rgba16f" -> VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
                case "rgba32f" -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
                default -> throw new IOException("Unsupported Vulkan image format '" + internalFormat + "' for shader-pack image " + name + ".");
            };
        }

        public int bytesPerTexel() throws IOException {
            return switch (internalFormat.toLowerCase(java.util.Locale.ROOT)) {
                case "r8ui", "r8" -> 1;
                case "r16ui", "r16f" -> 2;
                case "r32ui", "r32f" -> 4;
                case "rg8" -> 2;
                case "rg16f" -> 4;
                case "rg32f" -> 8;
                case "rgba8" -> 4;
                case "rgba16f" -> 8;
                case "rgba32f" -> 16;
                default -> throw new IOException("Cannot estimate memory for shader-pack image format '" + internalFormat + "'.");
            };
        }
    }
}
