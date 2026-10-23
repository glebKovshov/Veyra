package dev.veyra.shader;

import dev.veyra.vulkan.VeyraComputeResources;
import dev.veyra.vulkan.VeyraDeviceFeatures;
import dev.veyra.vulkan.VeyraImageDescriptor;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/** Owns active setup, begin, shadow, and post-processing compute stages and their shared images. */
public final class VeyraComputePipelineSet implements AutoCloseable {
    private final VeyraComputeResources resources;
    private final List<Dispatch> stages;
    private boolean closed;

    private VeyraComputePipelineSet(VeyraComputeResources resources, List<Dispatch> stages) {
        this.resources = resources;
        this.stages = List.copyOf(stages);
    }

    public static VeyraComputePipelineSet compile(ShaderPack pack) throws IOException {
        List<String> files = pack.activeComputeStageFiles();
        if (files.isEmpty()) return null;
        if (files.stream().anyMatch(file -> isShadowComputeFile(file))
                && !VeyraDeviceFeatures.supportsShadowVoxelWrites()) {
            throw new IOException("The Vulkan device cannot enable vertexPipelineStoresAndAtomics and "
                    + "shaderStorageImageExtendedFormats required by shadow voxel lighting.");
        }

        ShaderPackImageProperties properties = pack.readImageProperties();
        ShaderPackStorageBufferProperties storageBufferProperties = pack.readStorageBufferProperties();
        VeyraStorageBufferResources storageBuffers = VeyraStorageBufferResources.active();
        List<ComputeShaderAdapter.AdaptedStage> adapted = new ArrayList<>(files.size());
        for (String file : files) {
            String source = pack.readComputeStage(file)
                    .orElseThrow(() -> new IOException("The pack no longer contains " + file + "."));
            adapted.add(ComputeShaderAdapter.adapt(file, source, properties));
        }

        Set<String> graphicsImageNames = new LinkedHashSet<>();
        for (String file : pack.stageFiles().stream().sorted().toList()) {
            if (!file.endsWith(".vsh") && !file.endsWith(".fsh")) continue;
            String program = file.substring(0, file.lastIndexOf('.'));
            if (!pack.isProgramEnabled(program)) continue;
            var source = pack.readStage(file);
            if (source.isEmpty()) continue;
            var stage = ShaderPackImageBindings.prepare(file, source.get(), properties);
            stage.resources().stream().map(ComputeShaderAdapter.OpaqueResource::imageName)
                    .forEach(graphicsImageNames::add);
        }

        VeyraComputeResources resources = VeyraComputeResources.create(properties, adapted, graphicsImageNames);
        List<Dispatch> compiled = new ArrayList<>(adapted.size());
        try {
            for (int index = 0; index < adapted.size(); index++) {
                String file = files.get(index);
                ComputeShaderAdapter.AdaptedStage shader = adapted.get(index);
                String stageProgram = file.substring(0, file.length() - ".csh".length());
                String program = computeProgramName(file);
                ShaderPackStorageBufferProperties.IndirectDispatch indirect = null;
                VeyraStorageBufferResources.StorageBuffer indirectBuffer = null;
                if (storageBufferProperties.indirectDispatches().containsKey(stageProgram)) {
                    if (storageBuffers == null) {
                        throw new IOException("indirect." + stageProgram + " requires active shader-pack storage-buffer resources.");
                    }
                    indirect = storageBufferProperties.validateIndirectDispatch(
                            stageProgram, storageBuffers.viewWidth(), storageBuffers.viewHeight());
                    indirectBuffer = storageBuffers.storageBuffer(indirect.bufferBinding());
                }
                VeyraComputePipeline pipeline = VeyraComputePipeline.create(
                        stageProgram,
                        shader, resources.descriptors(index));
                compiled.add(new Dispatch(program, stageProgram, pipeline, shader.workGroupDispatch(),
                        shader.localSizeX(), shader.localSizeY(), indirectBuffer,
                        indirect == null ? 0 : indirect.offsetInBuffer()));
            }
            return new VeyraComputePipelineSet(resources, compiled);
        } catch (IOException | RuntimeException exception) {
            compiled.forEach(dispatch -> dispatch.pipeline().scheduleCleanUp());
            resources.close();
            throw exception;
        }
    }

    public void clearForFrame(int frameCounter) {
        ensureOpen();
        resources.clearForFrame(frameCounter);
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

    /** Binds native graphics descriptors to the same custom images used by shadowcomp. */
    void bindRenderPipelineImages(GraphicsPipeline pipeline,
                                  ShaderPackImageBindings.PreparedStage... stages) throws IOException {
        ensureOpen();
        var plan = ShaderPackImageBindings.plan(0, stages);
        List<ImageDescriptor> descriptors = pipeline.getImageDescriptors();
        for (var resource : plan.resources().values()) {
            ImageDescriptor nativeDescriptor = pipeline.getImageDescriptor(resource.name());
            if (nativeDescriptor == null) {
                throw new IOException("VulkanMod did not expose the custom image '" + resource.name() + "'.");
            }
            int descriptorIndex = descriptors.indexOf(nativeDescriptor);
            if (descriptorIndex < 0) {
                throw new IOException("VulkanMod's custom image '" + resource.name()
                        + "' is missing from the native image descriptor list.");
            }
            var nativeBinding = new ComputeShaderAdapter.OpaqueResource(resource.name(), resource.glslType(),
                    resource.qualifier(), resource.imageName(), resource.storageImage(), resource.format(),
                    nativeDescriptor.getBinding());
            VeyraImageDescriptor replacement = resources.graphicsDescriptors(List.of(nativeBinding)).get(0);
            if (resource.storageImage()) {
                if (!nativeDescriptor.isStorageImage()) {
                    throw new IOException("VulkanMod compiled graphics storage image '" + resource.name()
                            + "' as a sampled image.");
                }
                if (nativeDescriptor.getImage() != replacement.getImage()) {
                    throw new IOException("VulkanMod's graphics storage image '" + resource.name()
                            + "' is not bound to the shader-pack's shared image.");
                }
                if (nativeDescriptor.getLayout() != VK_IMAGE_LAYOUT_GENERAL) {
                    throw new IOException("Graphics storage image '" + resource.name()
                            + "' is not bound in GENERAL layout.");
                }
                continue;
            }
            // Shared images stay in GENERAL across shadow writes, compute dispatch and graphics sampling.
            // The descriptor layout must match the image's actual Vulkan layout.
            try {
                descriptors.set(descriptorIndex, replacement);
            } catch (UnsupportedOperationException exception) {
                throw new IOException("VulkanMod's native image descriptor list is immutable; cannot bind custom sampler '"
                        + resource.name() + "'.", exception);
            }
        }
    }

    VeyraImageDescriptor graphicsImageDescriptor(ComputeShaderAdapter.OpaqueResource binding) throws IOException {
        ensureOpen();
        return resources.graphicsDescriptors(List.of(binding)).getFirst();
    }

    /** Dispatches all shadow compute programs after the shadow render pass. */
    public void dispatchAfterShadowPass(int renderWidth, int renderHeight) {
        ensureOpen();
        dispatch(stages.stream().filter(stage -> isShadowComputeFile(stage.programName() + ".csh")).toList(),
                renderWidth, renderHeight);
    }

    /** Returns the active post-compute program names in Iris pass order. */
    public List<String> programNames(ShaderPassPhase phase) {
        ensureOpen();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Dispatch stage : stages) {
            if (isShadowComputeFile(stage.programName() + ".csh")
                    || isSetupComputeFile(stage.stageProgram() + ".csh")) continue;
            if (ShaderPassPhase.forProgram(stage.programName()) == phase) names.add(stage.programName());
        }
        return List.copyOf(names);
    }

    /** Returns begin compute programs in pass/component order for dispatch before the shadow map. */
    public List<String> beginProgramNames() {
        ensureOpen();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Dispatch stage : stages) {
            if (isBeginComputeFile(stage.stageProgram() + ".csh")) names.add(stage.programName());
        }
        return List.copyOf(names);
    }

    /** Dispatches begin compute programs before the world shadow pass. */
    public void dispatchBeginPrograms(int renderWidth, int renderHeight) {
        ensureOpen();
        for (String programName : beginProgramNames()) {
            dispatchProgram(programName, renderWidth, renderHeight);
        }
    }

    /** Returns setup compute programs in pass/component order for one-time pipeline initialization. */
    public List<String> setupProgramNames() {
        ensureOpen();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Dispatch stage : stages) {
            if (isSetupComputeFile(stage.stageProgram() + ".csh")) names.add(stage.programName());
        }
        return List.copyOf(names);
    }

    /** Dispatches setup programs once after the compute pipeline is installed or resized. */
    public void dispatchSetupPrograms(int renderWidth, int renderHeight) {
        ensureOpen();
        for (String programName : setupProgramNames()) {
            dispatchProgram(programName, renderWidth, renderHeight);
        }
    }

    public boolean hasShadowComputeStages() {
        ensureOpen();
        return stages.stream().anyMatch(stage -> isShadowComputeFile(stage.programName() + ".csh"));
    }

    /** Dispatches the component sequence attached to a single Iris post-processing program. */
    public void dispatchProgram(String programName, int renderWidth, int renderHeight) {
        ensureOpen();
        dispatch(stages.stream().filter(stage -> stage.programName().equals(programName)).toList(),
                renderWidth, renderHeight);
    }

    private void dispatch(List<Dispatch> selectedStages, int renderWidth, int renderHeight) {
        if (selectedStages.isEmpty()) return;
        List<ResolvedDispatch> resolvedStages = new ArrayList<>(selectedStages.size());
        for (Dispatch dispatch : selectedStages) {
            if (dispatch.indirectBuffer() != null) {
                resolvedStages.add(new ResolvedDispatch(dispatch.pipeline(), 0, 0, 0,
                        dispatch.indirectBuffer(), dispatch.indirectOffset()));
                continue;
            }
            int[] groups = dispatch.workGroups().resolve(renderWidth, renderHeight,
                    dispatch.localSizeX(), dispatch.localSizeY());
            if (groups[0] == 0 || groups[1] == 0 || groups[2] == 0) continue;
            resolvedStages.add(new ResolvedDispatch(dispatch.pipeline(), groups[0], groups[1], groups[2], null, 0));
        }
        if (resolvedStages.isEmpty()) return;
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        if (!Renderer.isRecording() || commandBuffer == null) {
            throw new IllegalStateException("Shader-pack compute stages can only dispatch while VulkanMod records a frame.");
        }
        if (Renderer.getInstance().getBoundRenderPass() != null) {
            throw new IllegalStateException("Shader-pack compute stages cannot dispatch inside a render pass.");
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT
                        | VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT
                        | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
        for (int index = 0; index < resolvedStages.size(); index++) {
            ResolvedDispatch dispatch = resolvedStages.get(index);
            if (dispatch.indirectBuffer() == null) {
                dispatch.pipeline().dispatchWorkGroups(dispatch.x(), dispatch.y(), dispatch.z());
            } else {
                dispatch.pipeline().dispatchIndirect(dispatch.indirectBuffer().buffer().getId(), dispatch.indirectOffset());
            }
            if (index + 1 < resolvedStages.size()) {
                VeyraComputePipeline.memoryBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                        VK_ACCESS_SHADER_WRITE_BIT,
                        VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            }
        }
        VeyraComputePipeline.memoryBarrier(commandBuffer,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT
                        | VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                VK_ACCESS_SHADER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
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

    private record Dispatch(String programName, String stageProgram,
                            VeyraComputePipeline pipeline,
                            ComputeWorkGroupDispatch workGroups,
                            int localSizeX, int localSizeY,
                            VeyraStorageBufferResources.StorageBuffer indirectBuffer, int indirectOffset) {
    }

    private record ResolvedDispatch(VeyraComputePipeline pipeline, int x, int y, int z,
                                    VeyraStorageBufferResources.StorageBuffer indirectBuffer, int indirectOffset) {
    }

    private static String computeProgramName(String file) {
        String program = file.substring(0, file.length() - ".csh".length());
        return program.replaceFirst("_[a-z]$", "");
    }

    private static boolean isShadowComputeFile(String file) {
        return file.matches("shadowcomp(?:[1-9][0-9]?)?(?:_[a-z])?\\.csh");
    }

    private static boolean isBeginComputeFile(String file) {
        return file.matches("begin(?:[1-9][0-9]?)?(?:_[a-z])?\\.csh");
    }

    private static boolean isSetupComputeFile(String file) {
        return file.matches("setup(?:[1-9][0-9]?)?(?:_[a-z])?\\.csh");
    }
}
