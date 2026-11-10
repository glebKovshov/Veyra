package dev.veyra.compat;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in for the frame graph Minecraft added in 1.21.2. Veyra's graphs import their targets and
 * chain passes through them, so running every pass in the order it was added preserves their
 * dependencies; nothing is pooled or culled.
 */
public final class FrameGraphBuilder {
    private final List<FramePass> passes = new ArrayList<>();

    public <T> ResourceHandle<T> importExternal(String name, T resource) {
        return new ResourceHandle<>(name, resource);
    }

    public FramePass addPass(String name) {
        FramePass pass = new FramePass(name);
        passes.add(pass);
        return pass;
    }

    public void execute(GraphicsResourceAllocator allocator) {
        execute(allocator, Inspector.NONE);
    }

    public void execute(GraphicsResourceAllocator allocator, Inspector inspector) {
        for (FramePass pass : passes) {
            inspector.beforeExecutePass(pass.name());
            pass.run();
            inspector.afterExecutePass(pass.name());
        }
    }

    public interface Inspector {
        Inspector NONE = new Inspector() { };

        default void acquireResource(String name) { }
        default void releaseResource(String name) { }
        default void beforeExecutePass(String name) { }
        default void afterExecutePass(String name) { }
    }
}
