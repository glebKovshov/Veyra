package dev.veyra.shader;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextSubmissionContextsTest {
    @Test
    void keepsDeferredTextOwnersBySubmitIdentityAndDropsUnrenderedSubmits() {
        TextSubmissionContexts contexts = new TextSubmissionContexts();
        SubmitNodeStorage.TextSubmit first = textSubmit();
        SubmitNodeStorage.TextSubmit equalButDistinct = textSubmit();
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
        SubmitNodeStorage.TextSubmit sign = textSubmit();
        SubmitNodeStorage.TextSubmit ordinary = textSubmit();
        contexts.capture(sign, 9);

        contexts.begin(sign);
        contexts.begin(ordinary);
        assertFalse(contexts.isBlockEntityText());
        contexts.end();
        assertTrue(contexts.isBlockEntityText());
        assertEquals(9, contexts.blockEntityId());
        contexts.end();
    }

    private static SubmitNodeStorage.TextSubmit textSubmit() {
        return new SubmitNodeStorage.TextSubmit(new PoseStack().last().pose(), 0.0F, 0.0F,
                FormattedCharSequence.EMPTY, false, Font.DisplayMode.POLYGON_OFFSET, 0, 0, 0, 0);
    }
}
