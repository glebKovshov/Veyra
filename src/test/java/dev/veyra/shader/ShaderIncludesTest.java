package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShaderIncludesTest {
    private String expand(String source, Map<String, String> files) throws IOException {
        return ShaderIncludes.expand("final.fsh", source, path -> Optional.ofNullable(files.get(path)));
    }

    @Test
    void ignoresCommentedIncludesAndRetainsShaderDirectives() throws IOException {
        String source = "/* RENDERTARGETS:0,1 */\n/*\n#include \"missing.glsl\"\n*/\n"
                + "// #include \"also-missing.glsl\"\nvoid main() {}\n";
        assertEquals(source, expand(source, Map.of()));
    }

    @Test
    void expandsRelativeAndRootPathsWithTrailingComments() throws IOException {
        String source = "#include \"lib/a.glsl\" // first\n";
        String result = expand(source, Map.of("lib/a.glsl", "#include </shared.glsl>\n#include \"../shared.glsl\"\n",
                "shared.glsl", "float sharedValue;"));
        assertEquals(2, result.split("float sharedValue;", -1).length - 1);
        assertTrue(result.contains("// first"));
        assertFalse(result.contains("#include"));
    }

    @Test
    void preservesCommentDelimitersAdjacentToInclude() throws IOException {
        String source = "/* comment\n*/ # include \"lib.glsl\" /* tail\n comment */\n";
        assertEquals("/* comment\n*/ float included;\n /* tail\n comment */\n",
                expand(source, Map.of("lib.glsl", "float included;")));
    }

    @Test
    void leavesConditionalMissingIncludesForCompiler() throws IOException {
        String result = expand("#if 0\n#include \"missing.glsl\"\n#endif\n", Map.of());
        assertTrue(result.startsWith("#if 0\n#error Shader include was not found: missing.glsl\n"));
        assertTrue(result.endsWith("#endif\n"));
    }

    @Test
    void guardedRecursionTerminatesAndKeepsTheGuard() throws IOException {
        String result = expand("#include \"guard.glsl\"\n", Map.of("guard.glsl",
                "#ifndef GUARD\n#define GUARD\n#include \"guard.glsl\"\n#endif\n"));
        assertTrue(result.contains("#ifndef GUARD\n#define GUARD\n#ifndef GUARD"));
        assertTrue(result.contains("#error Shader includes exceed"));
        assertTrue(result.contains("#endif"));
    }

    @Test
    void duplicateIncludesAreNotMistakenForCycles() throws IOException {
        String result = expand("#include \"a.glsl\"\n#include \"a.glsl\"\n", Map.of("a.glsl", "float a;"));
        assertEquals(2, result.split("float a;", -1).length - 1);
        assertFalse(result.contains("#error"));
    }

    @Test
    void rejectsDirectoryTraversal() {
        assertThrows(IOException.class, () -> expand("#include \"../secret\"\n", Map.of()));
        assertThrows(IOException.class, () -> ShaderIncludes.resolve("lib/a.glsl", "../../secret"));
        assertThrows(IOException.class, () -> ShaderIncludes.resolve("final.fsh", "/"));
    }

    @Test
    void boundsExpansionBeforeAppending() {
        assertThrows(IOException.class, () -> expand("x".repeat(4 * 1024 * 1024 + 1), Map.of()));
    }

    @Test
    void boundsDeepIncludeChains() throws IOException {
        String result = ShaderIncludes.expand("0.glsl", "#include \"1.glsl\"\n", path -> {
            int number = Integer.parseInt(path.substring(0, path.indexOf('.')));
            return Optional.of("#include \"" + (number + 1) + ".glsl\"\n");
        });
        assertTrue(result.contains("#error Shader includes exceed"));
        assertTrue(result.length() < 1024);
    }

    @Test
    void boundsBranchingRecursionAcrossSiblingIncludes() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        IOException failure = assertThrows(IOException.class, () -> ShaderIncludes.expand("final.fsh",
                "#include \"branch.glsl\"\n", path -> {
                    reads.incrementAndGet();
                    return Optional.of("#include \"branch.glsl\"\n#include \"branch.glsl\"\n");
                }));
        assertTrue(failure.getMessage().contains("expansion count"));
        assertTrue(reads.get() <= 4096);
    }

    @Test
    void acceptsCrLfAndBom() throws IOException {
        assertTrue(expand("\uFEFF#include \"a.glsl\"\r\n", Map.of("a.glsl", "float a;")).contains("float a;"));
    }
}
