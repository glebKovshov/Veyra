package dev.veyra.vulkan;

import dev.veyra.shader.ComputeShaderAdapter;
import dev.veyra.shader.ShaderPackImageProperties;
import net.vulkanmod.vulkan.device.DeviceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;

/** Allocates the active 3D image resources shared by a shader-pack compute stage's descriptors. */
public final class VeyraComputeResources implements AutoCloseable {
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int DEVICE_LOCAL_BUDGET_PERCENT = 60;

    private final Map<String, VeyraImage3D> images;
    private final Map<String, Boolean> clearEveryPass;
    private final Set<String> initialized = new LinkedHashSet<>();
    private final List<List<VeyraImageDescriptor>> descriptorsByStage;
    private final long estimatedBytes;
    private boolean closed;

    private VeyraComputeResources(Map<String, VeyraImage3D> images, Map<String, Boolean> clearEveryPass,
                                  List<List<VeyraImageDescriptor>> descriptorsByStage, long estimatedBytes) {
        this.images = Map.copyOf(images);
        this.clearEveryPass = Map.copyOf(clearEveryPass);
        this.descriptorsByStage = descriptorsByStage.stream().map(List::copyOf).toList();
        this.estimatedBytes = estimatedBytes;
    }

    public static VeyraComputeResources create(ShaderPackImageProperties properties,
                                               ComputeShaderAdapter.AdaptedStage shader) throws IOException {
        return create(properties, List.of(shader));
    }

    public static VeyraComputeResources create(ShaderPackImageProperties properties,
                                               List<ComputeShaderAdapter.AdaptedStage> shaders) throws IOException {
        Map<String, VeyraImage3D> images = new LinkedHashMap<>();
        Map<String, Boolean> clearEveryPass = new LinkedHashMap<>();
        List<List<VeyraImageDescriptor>> descriptorsByStage = new ArrayList<>();
        try {
            long estimatedBytes = 0;
            long budget = deviceLocalImageBudget();
            for (ComputeShaderAdapter.AdaptedStage shader : shaders) {
                List<VeyraImageDescriptor> descriptors = new ArrayList<>();
                for (ComputeShaderAdapter.OpaqueResource resource : shader.resources()) {
                    ShaderPackImageProperties.Image image = properties.images().get(resource.imageName());
                    if (image == null) {
                        throw new IOException("Compute resource '" + resource.name()
                                + "' references missing image." + resource.imageName() + ".");
                    }
                    if (!image.is3d()) {
                        throw new IOException("Compute resource '" + resource.name()
                                + "' uses 2D image." + image.name() + "; only 3D compute images are supported.");
                    }
                    VeyraImage3D volume = images.get(image.name());
                    if (volume == null) {
                        int[] dimensions = properties.resolveDimensions(image);
                        long imageBytes = estimateBytes(image, dimensions);
                        estimatedBytes = Math.addExact(estimatedBytes, imageBytes);
                        if (estimatedBytes > budget) {
                            throw new IOException("Shader-pack 3D images require " + mebibytes(estimatedBytes)
                                    + " MiB, above Veyra's conservative " + mebibytes(budget)
                                    + " MiB device-local image budget.");
                        }
                        volume = VeyraImage3D.create(image.name(), image.vulkanFormat(),
                                dimensions[0], dimensions[1], dimensions[2]);
                        images.put(image.name(), volume);
                        clearEveryPass.put(image.name(), image.clear());
                    }
                    descriptors.add(new VeyraImageDescriptor(resource.binding(), resource.glslType(),
                            resource.name(), volume, resource.storageImage()));
                }
                descriptorsByStage.add(List.copyOf(descriptors));
            }
            return new VeyraComputeResources(images, clearEveryPass, descriptorsByStage, estimatedBytes);
        } catch (ArithmeticException exception) {
            images.values().forEach(VeyraImage3D::free);
            throw new IOException("Shader-pack 3D image memory estimate overflowed.", exception);
        } catch (IOException | RuntimeException exception) {
            images.values().forEach(VeyraImage3D::free);
            throw exception;
        }
    }

    /** Clears newly allocated images once and Iris {@code clear=true} images before every shadow pass. */
    public void clearForShadowPass() {
        if (closed) throw new IllegalStateException("Compute image resources have already been released.");
        for (var entry : images.entrySet()) {
            String name = entry.getKey();
            if (!clearEveryPass.getOrDefault(name, false) && initialized.contains(name)) continue;
            entry.getValue().clearZero();
            initialized.add(name);
        }
    }

    public List<VeyraImageDescriptor> descriptors(int stageIndex) {
        return descriptorsByStage.get(stageIndex);
    }

    public Map<String, VeyraImage3D> images() {
        return images;
    }

    public long estimatedBytes() {
        return estimatedBytes;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        images.values().forEach(VeyraImage3D::free);
    }

    private static long estimateBytes(ShaderPackImageProperties.Image image, int[] dimensions) throws IOException {
        try {
            return Math.multiplyExact(Math.multiplyExact(Math.multiplyExact((long) dimensions[0], dimensions[1]),
                    dimensions[2]), image.bytesPerTexel());
        } catch (ArithmeticException exception) {
            throw new IOException("3D image." + image.name() + " is too large to estimate safely.", exception);
        }
    }

    private static long deviceLocalImageBudget() throws IOException {
        if (DeviceManager.memoryProperties == null) {
            throw new IOException("Vulkan device memory properties are unavailable for compute image allocation.");
        }
        long largestDeviceLocalHeap = 0;
        var heaps = DeviceManager.memoryProperties.memoryHeaps();
        for (int index = 0; index < DeviceManager.memoryProperties.memoryHeapCount(); index++) {
            var heap = heaps.get(index);
            if ((heap.flags() & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0) {
                largestDeviceLocalHeap = Math.max(largestDeviceLocalHeap, heap.size());
            }
        }
        if (largestDeviceLocalHeap <= 0) {
            throw new IOException("The selected Vulkan device reports no device-local memory heap.");
        }
        return largestDeviceLocalHeap * DEVICE_LOCAL_BUDGET_PERCENT / 100;
    }

    private static long mebibytes(long bytes) {
        return (bytes + MEBIBYTE - 1) / MEBIBYTE;
    }
}
