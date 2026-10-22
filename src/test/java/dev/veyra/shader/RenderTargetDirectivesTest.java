package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderTargetDirectivesTest {
    @Test
    void selectsTheDirectiveInTheActivePackBranch() throws Exception {
        String source = "#define QUALITY 2\n"
                + "#if QUALITY >= 2\n/* DRAWBUFFERS:064 */\n"
                + "#else\n/* DRAWBUFFERS:06 */\n#endif\n";

        assertEquals(java.util.List.of(0, 6, 4), RenderTargetDirectives.activeTargets(source));
    }

    @Test
    void ignoresRenderTargetCommentsInInactiveBranches() throws Exception {
        String source = "#if 0\n/* DRAWBUFFERS:064 */\n"
                + "#else\n/* DRAWBUFFERS:06 */\n#endif\n";

        assertEquals(java.util.List.of(0, 6), RenderTargetDirectives.activeTargets(source));
        String selected = RenderTargetDirectives.keepActiveComments("test.fsh", source);
        assertTrue(selected.contains("DRAWBUFFERS:06"), selected);
        assertFalse(selected.contains("DRAWBUFFERS:064"), selected);
    }

    @Test
    void usesTheLastActiveDirective() throws Exception {
        assertEquals(java.util.List.of(0, 6), RenderTargetDirectives.activeTargets(
                "/* DRAWBUFFERS:06 */\n/* DRAWBUFFERS:06 */\n"));
        assertEquals(java.util.List.of(0, 4), RenderTargetDirectives.activeTargets(
                "/* DRAWBUFFERS:06 */\n/* DRAWBUFFERS:04 */\n"));
        assertEquals(java.util.List.of(0, 3, 4), RenderTargetDirectives.activeTargets(
                "/* RENDERTARGETS:0,6 */\n/* DRAWBUFFERS:034 */\n"));
    }

    @Test
    void onlyActiveOccurrencesParticipateInLastDirectiveSelection() throws Exception {
        String source = "/* DRAWBUFFERS:06 */\n#if 0\n/* RENDERTARGETS:0,4 */\n#endif\n";

        assertEquals(java.util.List.of(0, 6), RenderTargetDirectives.activeTargets(source));
    }
}
