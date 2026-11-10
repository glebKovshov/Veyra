package dev.veyra.compat;

/** Stand-in for the frame graph's resource allocator; Veyra's graphs only import resources. */
public interface GraphicsResourceAllocator {
    GraphicsResourceAllocator UNPOOLED = new GraphicsResourceAllocator() { };
}
