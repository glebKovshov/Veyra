package dev.veyra.shader;

import java.util.List;

public final class LegacyTextContextCheck {
    public static void run() {
        var contexts = new TextSubmissionContexts();
        Object sign = new String("same value"), other = new String("same value");
        assert sign.equals(other) && sign != other;
        contexts.capture(sign, 17);
        contexts.capture(other, 29);
        contexts.begin(sign);
        assert contexts.isBlockEntityText() && contexts.blockEntityId() == 17;
        contexts.begin(other);
        assert contexts.blockEntityId() == 29;
        contexts.end();
        assert contexts.blockEntityId() == 17;
        contexts.begin(new Object());
        assert !contexts.isBlockEntityText() && contexts.blockEntityId() == -1;
        contexts.end();
        assert contexts.blockEntityId() == 17;
        contexts.end(); contexts.end();
        assert !contexts.isBlockEntityText() && contexts.blockEntityId() == -1;
        contexts.begin(sign);
        assert !contexts.isBlockEntityText() : "Consumed submission retained its owner";
        contexts.end();
        contexts.capture(sign, 31);
        contexts.clear(List.of(other));
        contexts.begin(sign);
        assert contexts.blockEntityId() == 31 : "Value equality cleared a different submission";
        contexts.end();
        contexts.capture(sign, 42);
        contexts.clear(List.of(sign));
        contexts.begin(sign);
        assert !contexts.isBlockEntityText();
        contexts.end();
        System.out.println("Version-independent text identity/context checks passed");
    }
}
