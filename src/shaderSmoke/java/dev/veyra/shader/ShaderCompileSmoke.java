package dev.veyra.shader;

import java.nio.file.*;
import java.util.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class ShaderCompileSmoke {
    public static void main(String[] args) throws Exception {
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_0);
        shaderc_compile_options_set_auto_bind_uniforms(options, true);
        shaderc_compile_options_set_auto_map_locations(options, true);
        int count = 0;
        int compiled = 0;
        try {
            try (var files = Files.walk(Path.of("examples"))) {
                for (Path path : files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().matches(
                        "gbuffers_(terrain(?:_solid|_cutout)?|water|entities(?:_translucent)?|clouds)\\.fsh")).sorted().toList()) {
                    String name = path.getFileName().toString();
                    String source = ShaderIncludes.expand(name, Files.readString(path), included -> {
                        Path file = path.getParent().resolve(included);
                        return Files.exists(file) ? Optional.of(Files.readString(file)) : Optional.empty();
                    });
                    String adapted = name.equals("gbuffers_clouds.fsh") ? CloudShaderAdapter.adaptFragment(source)
                            : name.startsWith("gbuffers_entities") ? EntityShaderAdapter.adaptFragment(source)
                            : TerrainShaderAdapter.adaptFragment(source);
                    long preprocessed = shaderc_compile_into_preprocessed_text(compiler, adapted, shaderc_fragment_shader,
                            path.toString(), "main", options);
                    try {
                        if (shaderc_result_get_compilation_status(preprocessed) != shaderc_compilation_status_success)
                            throw new AssertionError(shaderc_result_get_error_message(preprocessed));
                    } finally { shaderc_result_release(preprocessed); }
                    // Entity/cloud uniforms require VulkanMod's separate descriptor/UBO conversion.
                    if (!java.util.regex.Pattern.compile("(?m)^uniform\\s+(?!sampler)").matcher(adapted).find()) {
                        compile(compiler, options, adapted, path.toString(), true);
                        compiled++;
                    }
                    count++;
                }
            }
            if (count == 0) throw new AssertionError("No example shaders found.");
            String inactive = ShaderIncludes.expand("final.fsh", "#if 0\n#include \"missing.glsl\"\n#endif\n", p -> Optional.empty());
            compile(compiler, options, "#version 450\n" + inactive + "layout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}\n", "inactive-missing-include", true);
            String active = ShaderIncludes.expand("final.fsh", "#include \"missing.glsl\"\n", p -> Optional.empty());
            compile(compiler, options, "#version 450\n" + active + "void main(){}\n", "active-missing-include", false);
            String guarded = ShaderIncludes.expand("final.fsh", "#include \"guard.glsl\"\n", p -> Optional.of(
                    "#ifndef GUARD\n#define GUARD\n#include \"guard.glsl\"\n#endif\n"));
            compile(compiler, options, "#version 450\n" + guarded + "layout(location=0) out vec4 color;\nvoid main(){color=vec4(1);}\n", "guarded-recursion", true);
            String recursive = ShaderIncludes.expand("loop.glsl", "#include \"loop.glsl\"\n", p -> Optional.of("#include \"loop.glsl\"\n"));
            compile(compiler, options, "#version 450\n" + recursive + "void main(){}\n", "unguarded-recursion", false);
            System.out.println("Preprocessed " + count + " example shaders, compiled " + compiled + " directly to Vulkan SPIR-V; 4 conditional-include compiler checks passed.");
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }
    private static void compile(long compiler, long options, String source, String name, boolean expected) {
        long result = shaderc_compile_into_spv(compiler, source, shaderc_fragment_shader, name, "main", options);
        try {
            boolean success = shaderc_result_get_compilation_status(result) == shaderc_compilation_status_success;
            if (success != expected) throw new AssertionError(name + ": " + shaderc_result_get_error_message(result));
            System.out.println("PASS " + name);
        } finally { shaderc_result_release(result); }
    }
}
