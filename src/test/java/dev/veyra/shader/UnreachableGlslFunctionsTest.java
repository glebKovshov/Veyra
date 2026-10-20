package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnreachableGlslFunctionsTest {
    @Test
    void removesOneUnreachableBodyWhenThePermissiveMatcherFindsNestedFunctionLikeText() {
        String source = """
                void unused() {
                    void nestedFunctionLikeText() { float ignored = 1.0; }
                }
                void main() { }
                """;

        String pruned = UnreachableGlslFunctions.removeUnreachable(source, "main");

        assertFalse(pruned.contains("nestedFunctionLikeText"), pruned);
        assertFalse(pruned.contains("void unused()"), pruned);
        assertTrue(pruned.contains("void main()"), pruned);
    }
}
