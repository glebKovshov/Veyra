package dev.veyra.vulkan;

/** Operations shared by Veyra-owned shader-pack images used by graphics and compute stages. */
public interface VeyraCustomImage {
    void clearZero();

    boolean supportsLinearFiltering();
}
