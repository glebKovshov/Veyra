package dev.veyra.compat;

/** A pass of {@link FrameGraphBuilder}; reads and writes only document ordering here. */
public final class FramePass {
    private final String name;
    private Runnable task = () -> { };

    FramePass(String name) { this.name = name; }

    public String name() { return name; }

    public void reads(ResourceHandle<?> handle) { }

    public <T> ResourceHandle<T> readsAndWrites(ResourceHandle<T> handle) { return handle; }

    public void disableCulling() { }

    public void executes(Runnable task) { this.task = task; }

    void run() { task.run(); }
}
