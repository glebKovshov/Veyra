package dev.veyra.smoke;

import dev.veyra.shader.ComputeShaderAdapter;
import dev.veyra.shader.ShaderPackImageProperties;
import dev.veyra.shader.VeyraComputePipeline;
import dev.veyra.vulkan.VeyraComputeResources;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.*;

/** Development-only GPU check of custom-image clear, integer sampling, compute writes and retirement. */
final class ComputeImageProbe implements AutoCloseable {
    private static final int TEXELS = 4 * 4 * 4;
    private VeyraComputeResources resources;
    private VeyraComputePipeline pipeline;
    private Buffer readback;

    static ComputeImageProbe record() throws IOException {
        ComputeImageProbe probe = new ComputeImageProbe();
        try {
            var properties = ShaderPackImageProperties.parse(
                    "image.probe_img = probe_sampler red_integer r16ui unsigned_int false false 4 4 4");
            var shader = ComputeShaderAdapter.adapt("shadowcomp.csh", """
                    #version 430
                    layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;
                    const ivec3 workGroups = ivec3(4, 4, 4);
                    uniform float framemod2;
                    uniform usampler3D probe_sampler;
                    writeonly uniform uimage3D probe_img;
                    void main() {
                        ivec3 pos = ivec3(gl_GlobalInvocationID);
                        uint previous = texelFetch(probe_sampler, pos, 0).r;
                        imageStore(probe_img, pos, uvec4(previous + 42u + uint(framemod2) * 0u));
                    }
                    """, properties);
            probe.resources = VeyraComputeResources.create(properties, shader);
            probe.pipeline = VeyraComputePipeline.create("veyra_compute_image_probe", shader, probe.resources.descriptors(0));
            probe.resources.clearForShadowPass();
            probe.pipeline.dispatchWorkGroups(shader.workGroupsX(), shader.workGroupsY(), shader.workGroupsZ());
            probe.readback = new Buffer("Veyra compute volume evidence", VK_BUFFER_USAGE_TRANSFER_DST_BIT, MemoryTypes.HOST_MEM);
            probe.readback.createBuffer(TEXELS * Short.BYTES);
            var image = probe.resources.images().get("probe_img");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var commands = Renderer.getCommandBuffer();
                image.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
                var copy = VkBufferImageCopy.calloc(1, stack);
                copy.imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                copy.imageExtent().set(4, 4, 4);
                vkCmdCopyImageToBuffer(commands, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, probe.readback.getId(), copy);
                VeyraComputePipeline.memoryBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT,
                        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_HOST_READ_BIT);
                image.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_GENERAL);
            }
            return probe;
        } catch (IOException | RuntimeException failure) {
            probe.close();
            throw failure;
        }
    }

    void verify() throws IOException {
        for (int index = 0; index < TEXELS; index++) {
            int value = Short.toUnsignedInt(MemoryUtil.memGetShort(readback.getDataPtr() + (long) index * Short.BYTES));
            if (value != 42) throw new IOException("Compute image texel " + index + " was " + value + ", expected 42.");
        }
        Path output = Path.of(System.getProperty("veyra.graphicsSmoke.output", "graphics-smoke-output"));
        Files.createDirectories(output);
        Files.writeString(output.resolve("compute-image-probe.json"),
                "{\"status\":\"passed\",\"format\":\"r16ui\",\"texels\":64,\"expected\":42}");
    }

    @Override
    public void close() {
        if (pipeline != null) { pipeline.scheduleCleanUp(); pipeline = null; }
        if (resources != null) { resources.close(); resources = null; }
        if (readback != null) { readback.scheduleFree(); readback = null; }
    }
}
