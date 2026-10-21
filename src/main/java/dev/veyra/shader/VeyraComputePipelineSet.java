package dev.veyra.shader;

import dev.veyra.vulkan.VeyraComputeResources;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

/** Owns the active shadowcomp compute sequence and its shared images. */
public final class VeyraComputePipelineSet implements AutoCloseable {
    private final VeyraComputeResources resources;
    private final List<Dispatch> stages;
    private boolean closed;

    private VeyraComputePipelineSet(VeyraComputeResources resources, List<Dispatch> stages) {
        this.resources = resources;
        this.stages = List.copyOf(stages);
    }

    public static VeyraComputePipelineSet compile(ShaderPack pack) throws IOException {
        List<String> files = pack.activeComputeStageFiles("shadowcomp");
        if (files.isEmpty()) return null;
        if (files.stream().anyMatch(file -> file.startsWith("shadowcomp"))
                && !VeyraDeviceFeatures.supportsShadowVoxelWrites()) {
            throw new IOException("The Vulkan device cannot enable vertexPipelineStoresAndAtomics and "
                    + "shaderStorageImageExtendedFormats required by shadow voxel lighting.");
        }

        ShaderPackImageProperties properties = pack.readImageProperties();
        List<ComputeShaderAdapter.AdaptedStage> adapted = new ArrayList<>(files.size());
        for (String file : files) {
            String source = pack.readComputeStage(file)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + file + "."));
            adapted.add(ComputeShaderAdapter.adapt(file, source, properties));
        }

        VeyraComputeResources resources = VeyraComputeResources.create(properties, adapted);
        List<Dispatch> compiled = new ArrayList<>(adapted.size());
        try {
            for (int index = 0; index < adapted.size(); index++) {
                String file = files.get(index);
                ComputeShaderAdapter.AdaptedStage shader = adapted.get(index);
                boolean hasStorageImage = shader.resources().stream()
                        .filter(ComputeShaderAdapter.OpaqueResource::storageImage)
                        .findAny().isPresent();
                if (!hasStorageImage) {
                    throw new IOException(file + " does not declare an active storage image; Veyra shadow compute currently requires image resources.");
                }
                if (!shader.hasFixedWorkGroups()) {
                    throw new IOException(file + " has no fixed const ivec3 workGroups; render-relative or indirect compute sizing is not implemented.");
                }
                VeyraComputePipeline pipeline = VeyraComputePipeline.create(
                        file.substring(0, file.length() - 4),
                        shader, resources.descriptors(index));
                compiled.add(new Dispatch(pipeline, shader.workGroupsX(), shader.workGroupsY(), shader.workGroupsZ()));
            }
            return new VeyraComputePipelineSet(resources, compiled);
        } catch (IOException | RuntimeException exception) {
            compiled.forEach(dispatch -> dispatch.pipeline().scheduleCleanUp());
            resources.close();
            throw exception;
        }
    }

    public void clearForShadowPass() {
        ensureOpen();
        resources.clearForShadowPass();
    }

    ShaderPackImageBindings.Plan bindGraphicsImages(Pipeline.Builder builder,
                                                   ShaderPackImageBindings.PreparedStage... stages) throws IOException {
        ensureOpen();
        int firstBinding = builder.getUBOs().size() + builder.getImageDescriptors().size();
        var plan = ShaderPackImageBindings.plan(firstBinding, stages);
        for (var descriptor : resources.graphicsDescriptors(List.copyOf(plan.resources().values()))) {
            builder.addImageDescriptor(descriptor);
        }
        return plan;
    }

    /** Dispatches after all shadow stages, matching Iris shadowcomp ordering, then exposes writes to graphics. */
    public void dispatchAfterShadowPass() {
        ensureOpen();
        if (stages.isEmpty()) return;
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if (!Renderer.isRecording() || commandBuffer == null) {
            throw new IllegalStateException("Shader-pack compute stages can only dispatch while VulkanMod records a frame.");
        }
        if (Renderer.getInstance().getBoundRenderPass() != null) {
            throw new IllegalStateException("Shader-pack compute stages cannot dispatch inside a render pass.");
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
        for (int index = 0; index < stages.size(); index++) {
            Dispatch dispatch = stages.get(index);
            dispatch.pipeline().dispatchWorkGroups(dispatch.x(), dispatch.y(), dispatch.z());
            if (index + 1 < stages.size()) {
                VeyraComputePipeline.memoryBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
            }
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT,
                VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);
    }

    public int stageCount() {
        return stages.size();
    }

    public long estimatedImageBytes() {
        return resources.estimatedBytes();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stages.forEach(dispatch -> dispatch.pipeline().scheduleCleanUp());
        resources.close();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Compute pipeline set has already been released.");
    }

    private record Dispatch(VeyraComputePipeline pipeline, int x, int y, int z) {
    }
}
