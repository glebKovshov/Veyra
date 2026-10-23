package dev.veyra.vulkan;

import dev.veyra.shader.ComputeShaderAdapter;
import dev.veyra.shader.ShaderPackImageProperties;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;

/** Allocates active shader-pack image resources shared by compute and graphics stage descriptors. */
public final class VeyraComputeResources implements AutoCloseable {
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int DEVICE_LOCAL_BUDGET_PERCENT = 60;

    private final Map<String, VulkanImage> images;
    private final Map<String, Boolean> clearEveryPass;
    private final Set<String> initialized = new LinkedHashSet<>();
    private final List<List<VeyraImageDescriptor>> descriptorsByStage;
    private final long estimatedBytes;
    private int lastClearedFrame = Integer.MIN_VALUE;
    private boolean closed;

    private VeyraComputeResources(Map<String, VulkanImage> images, Map<String, Boolean> clearEveryPass,
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
        return create(properties, shaders, Set.of());
    }

    public static VeyraComputeResources create(ShaderPackImageProperties properties,
                                               List<ComputeShaderAdapter.AdaptedStage> shaders,
                                               Set<String> graphicsImageNames) throws IOException {
        Map<String, VulkanImage> images = new LinkedHashMap<>();
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
                    VulkanImage volume = images.get(image.name());
                    if (volume == null) {
                        int[] dimensions = properties.resolveDimensions(image);
                        long imageBytes = estimateBytes(image, dimensions);
                        estimatedBytes = Math.addExact(estimatedBytes, imageBytes);
                        if (estimatedBytes > budget) {
                            throw new IOException("Shader-pack custom images require " + mebibytes(estimatedBytes)
                                    + " MiB, above Veyra's conservative " + mebibytes(budget)
                                    + " MiB device-local image budget.");
                        }
                        volume = createImage(image, dimensions);
                        images.put(image.name(), volume);
                        clearEveryPass.put(image.name(), image.clear());
                    }
                    descriptors.add(new VeyraImageDescriptor(resource.binding(), resource.glslType(),
                            resource.name(), volume, resource.storageImage()));
                }
                descriptorsByStage.add(List.copyOf(descriptors));
            }
            for (String imageName : graphicsImageNames) {
                ShaderPackImageProperties.Image image = properties.images().get(imageName);
                if (image == null) {
                    throw new IOException("Graphics stage references missing image." + imageName + ".");
                }
                if (images.containsKey(imageName)) continue;
                int[] dimensions = properties.resolveDimensions(image);
                long imageBytes = estimateBytes(image, dimensions);
                estimatedBytes = Math.addExact(estimatedBytes, imageBytes);
                if (estimatedBytes > budget) {
                    throw new IOException("Shader-pack custom images require " + mebibytes(estimatedBytes)
                            + " MiB, above Veyra's conservative " + mebibytes(budget)
                            + " MiB device-local image budget.");
                }
                VulkanImage resource = createImage(image, dimensions);
                images.put(imageName, resource);
                clearEveryPass.put(imageName, image.clear());
            }
            return new VeyraComputeResources(images, clearEveryPass, descriptorsByStage, estimatedBytes);
        } catch (ArithmeticException exception) {
            images.values().forEach(VulkanImage::free);
            throw new IOException("Shader-pack custom image memory estimate overflowed.", exception);
        } catch (IOException | RuntimeException exception) {
            images.values().forEach(VulkanImage::free);
            throw exception;
        }
    }

    /** Initializes new images and clears Iris {@code clear=true} images once per render frame. */
    public void clearForFrame(int frameCounter) {
        if (closed) throw new IllegalStateException("Compute image resources have already been released.");
        for (var entry : images.entrySet()) {
            String name = entry.getKey();
            boolean clear = clearEveryPass.getOrDefault(name, false);
            if (clear && lastClearedFrame == frameCounter) continue;
            if (!clear && initialized.contains(name)) continue;
            ((VeyraCustomImage) entry.getValue()).clearZero();
            initialized.add(name);
        }
        lastClearedFrame = frameCounter;
    }

    public List<VeyraImageDescriptor> descriptors(int stageIndex) {
        return descriptorsByStage.get(stageIndex);
    }

    /** Creates graphics bindings borrowing the very same images owned by this resource set. */
    public List<VeyraImageDescriptor> graphicsDescriptors(List<ComputeShaderAdapter.OpaqueResource> bindings) throws IOException {
        if (closed) throw new IllegalStateException("Custom image resources have already been released.");
        List<VeyraImageDescriptor> descriptors = new ArrayList<>();
        for (var binding : bindings) {
            VulkanImage image = images.get(binding.imageName());
            if (image == null) throw new IOException("Graphics uniform '" + binding.name()
                    + "' references image." + binding.imageName() + " absent from the shared compute resource set.");
            descriptors.add(new VeyraImageDescriptor(binding.binding(), binding.glslType(), binding.name(), image, binding.storageImage()));
        }
        return List.copyOf(descriptors);
    }

    public Map<String, VulkanImage> images() {
        return images;
    }

    public long estimatedBytes() {
        return estimatedBytes;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        images.values().forEach(VulkanImage::free);
    }

    private static long estimateBytes(ShaderPackImageProperties.Image image, int[] dimensions) throws IOException {
        try {
            long texels = Math.multiplyExact((long) dimensions[0], dimensions[1]);
            if (dimensions.length == 3) texels = Math.multiplyExact(texels, dimensions[2]);
            return Math.multiplyExact(texels, image.bytesPerTexel());
        } catch (ArithmeticException exception) {
            throw new IOException("Custom image." + image.name() + " is too large to estimate safely.", exception);
        }
    }

    private static VulkanImage createImage(ShaderPackImageProperties.Image image, int[] dimensions) throws IOException {
        if (image.is3d()) {
            return VeyraImage3D.create(image.name(), image.vulkanFormat(),
                    dimensions[0], dimensions[1], dimensions[2]);
        }
        return VeyraImage2D.create(image.name(), image.vulkanFormat(), dimensions[0], dimensions[1]);
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
