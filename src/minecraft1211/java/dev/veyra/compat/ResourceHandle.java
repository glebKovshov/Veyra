package dev.veyra.compat;

/** A resource of {@link FrameGraphBuilder}; imported resources are used as they are. */
public final class ResourceHandle<T> {
    private final String name;
    private final T value;

    ResourceHandle(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T get() { return value; }

    @Override public String toString() { return name; }
}
