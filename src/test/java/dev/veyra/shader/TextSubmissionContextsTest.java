package dev.veyra.shader;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextSubmissionContextsTest {
    @Test
    void keepsDeferredTextOwnersBySubmitIdentityAndDropsUnrenderedSubmits() {
        TextSubmissionContexts contexts = new TextSubmissionContexts();
        TextSubmit first = textSubmit();
        TextSubmit equalButDistinct = textSubmit();
        assertEquals(first, equalButDistinct);

        contexts.capture(first, 12);
        contexts.capture(equalButDistinct, 37);
        contexts.begin(equalButDistinct);
        assertTrue(contexts.isBlockEntityText());
        assertEquals(37, contexts.blockEntityId());
        contexts.end();

        contexts.clear(List.of(first));
        contexts.begin(first);
        assertFalse(contexts.isBlockEntityText());
        assertEquals(BlockIdMap.UNMAPPED, contexts.blockEntityId());
        contexts.end();
    }

    @Test
    void nestedTextRenderContextsRestoreTheirParent() {
        TextSubmissionContexts contexts = new TextSubmissionContexts();
        TextSubmit sign = textSubmit();
        TextSubmit ordinary = textSubmit();
        contexts.capture(sign, 9);

        contexts.begin(sign);
        contexts.begin(ordinary);
        assertFalse(contexts.isBlockEntityText());
        contexts.end();
        assertTrue(contexts.isBlockEntityText());
        assertEquals(9, contexts.blockEntityId());
        contexts.end();
    }

    private record TextSubmit(String text) { }

    private static TextSubmit textSubmit() {
        return new TextSubmit("equal text");
    }
}
