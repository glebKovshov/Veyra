package dev.veyra.shader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Submission keys are opaque identities; this tracker never reads version-specific node data. */
final class TextSubmissionContexts {
    private static final Context NONE = new Context(false, BlockIdMap.UNMAPPED);

    private final Map<Object, Context> pending = new IdentityHashMap<>();
    private final ThreadLocal<Deque<Context>> rendering = ThreadLocal.withInitial(ArrayDeque::new);

    void capture(Object textSubmit, int blockEntityId) {
        synchronized (pending) {
            pending.put(textSubmit, new Context(true, blockEntityId));
        }
    }

    void begin(Object textSubmit) {
        Context context;
        synchronized (pending) {
            context = pending.remove(textSubmit);
        }
        rendering.get().push(context == null ? NONE : context);
    }

    void end() {
        Deque<Context> contexts = rendering.get();
        if (!contexts.isEmpty()) contexts.pop();
        if (contexts.isEmpty()) rendering.remove();
    }

    void clear(List<?> textSubmits) {
        synchronized (pending) {
            textSubmits.forEach(pending::remove);
        }
    }

    boolean isBlockEntityText() {
        return current().blockEntity();
    }

    int blockEntityId() {
        return current().blockEntityId();
    }

    private Context current() {
        Deque<Context> contexts = rendering.get();
        if (contexts.isEmpty()) {
            rendering.remove();
            return NONE;
        }
        return contexts.peek();
    }

    private record Context(boolean blockEntity, int blockEntityId) {
    }
}
