package dev.veyra.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderPass;
import dev.veyra.mixin.accessor.LegacyGpuTextureSamplerAccessor;
import java.lang.reflect.Proxy;

public final class LegacyTextureViewCheck {
    public static void main(String[] args) throws Exception {
        checkBootstrapStack();
        LegacyVertexAttributesCheck.run();
        Texture texture = new Texture();
        var view = new LegacyTextureView(texture, 1, 2);
        var sibling = new LegacyTextureView(texture, 0, 4);
        assert view.texture() == texture;
        assert view.baseMipLevel() == 1 && view.mipLevels() == 2;
        assert view.getWidth(0) == 16 && view.getWidth(1) == 8;
        assert view.getHeight(0) == 8 && view.getHeight(1) == 4;
        rejects(() -> new LegacyTextureView(texture, -1, 1));
        rejects(() -> new LegacyTextureView(texture, 0, 0));
        rejects(() -> new LegacyTextureView(texture, 4, 1));
        rejects(() -> new LegacyTextureView(texture, 1, Integer.MAX_VALUE));
        rejects(() -> view.getWidth(-1));
        rejects(() -> view.getHeight(2));
        rejects(() -> LegacyVkTextureView.create(texture));
        try { view.textureForBinding(); throw new AssertionError("Subrange was bound as a full texture"); }
        catch (UnsupportedOperationException expected) { }
        assert sibling.textureForBinding() == texture;
        view.close(); view.close();
        assert view.isClosed() && !texture.isClosed() && !sibling.isClosed();
        try { view.textureForBinding(); throw new AssertionError("Closed view was accepted"); }
        catch (IllegalStateException expected) { }
        texture.close();
        assert sibling.isClosed();
        Texture sampledTexture = new Texture();
        LegacyTextureView sampled = new LegacyTextureView(sampledTexture, 0, 4);
        LegacySampler sampler = LegacySampler.getSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.NEAREST, true);
        int[] calls = {0};
        RenderPass pass = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[]{RenderPass.class}, (proxy, method, values) -> {
                    if (!method.getName().equals("bindSampler")) throw new AssertionError(method);
                    assert values[0].equals("test");
                    assert values[1] == (calls[0]++ == 0 ? sampledTexture : null);
                    return null;
                });
        LegacySampler.bind(pass, "test", sampled, sampler);
        assert LegacySampler.from(sampledTexture).equals(sampler);
        LegacySampler.bind(pass, "test", null, null);
        assert calls[0] == 2;
        LegacySampler original = LegacySampler.from(sampledTexture);
        LegacySampler different = LegacySampler.getClampToEdge(FilterMode.NEAREST);
        LegacyTextureView subrange = new LegacyTextureView(sampledTexture, 1, 1);
        try {
            LegacySampler.bind(pass, "test", subrange, different);
            throw new AssertionError("Unsupported binding was accepted");
        } catch (UnsupportedOperationException expected) { }
        assert LegacySampler.from(sampledTexture).equals(original) : "Rejected binding changed texture state";
        assert calls[0] == 2 : "Rejected binding reached the native render pass";
        sampled.close();
        try {
            LegacySampler.bind(pass, "test", sampled, different);
            throw new AssertionError("Closed binding was accepted");
        } catch (IllegalStateException expected) { }
        assert LegacySampler.from(sampledTexture).equals(original);
        assert calls[0] == 2;
        sampler.close(); sampled.close();
        assert !sampledTexture.isClosed();
        checkRenderTargets();
        checkTargetAllocation();
        checkDescriptorLookup();
        checkNativeSamplerFlags();
        dev.veyra.shader.VeyraPostInputCheck.run();
        dev.veyra.shader.LegacyStd140Check.run();
        checkVertexUpload();
        checkNativeUniformBlock();
        checkImageDescriptorTypes();
        checkTextureSlots();
        LegacyRenderHookCheck.run();
        LegacyPipelineCheck.run();
        dev.veyra.shader.VeyraEnvironmentCheck.run();
        dev.veyra.shader.VeyraPostProjectionCheck.run();
        dev.veyra.shader.LegacyGlslSourceCheck.run();
        dev.veyra.shader.LegacyPostUniformsCheck.run();
        LegacyUniformMetadataCheck.run();
        LegacyStorageDescriptorCheck.run();
        LegacyUniformUploadCheck.run();
        dev.veyra.shader.LegacyTextContextCheck.run();
        checkDepthViewOwnership();
        System.out.println("Legacy texture view range and ownership checks passed");
    }

    private static void checkBootstrapStack() {
        var configuration = org.lwjgl.system.Configuration.STACK_SIZE;
        Integer previous = configuration.get();
        try {
            var bootstrap = new dev.veyra.VeyraLegacyBootstrap();
            configuration.set(64);
            bootstrap.onPreLaunch();
            assert configuration.get() == 256;
            assert org.lwjgl.system.MemoryStack.stackGet().getSize() >= 256 * 1024;
            configuration.set(512);
            bootstrap.onPreLaunch();
            assert configuration.get() == 512 : "Bootstrap lowered a larger configured stack";
        } finally { configuration.set(previous); }
    }
    private static void checkDepthViewOwnership() {
        VulkanTexture original = new VulkanTexture();
        VulkanTexture sampledAlias = new VulkanTexture();
        int[] releases = {0};
        var view = new VeyraDepthTextureView(sampledAlias, () -> {
            releases[0]++;
            sampledAlias.close();
        });
        assert view.texture() == sampledAlias && !view.isClosed();
        assert view.textureForBinding() == sampledAlias;
        view.close(); view.close();
        assert releases[0] == 1 && view.isClosed() && sampledAlias.isClosed() && !original.isClosed();
        VulkanTexture externallyClosedAlias = new VulkanTexture();
        var externallyClosedView = new VeyraDepthTextureView(externallyClosedAlias, () -> releases[0]++);
        externallyClosedAlias.close();
        assert externallyClosedView.isClosed();
        externallyClosedView.close(); externallyClosedView.close();
        assert releases[0] == 2 : "Alias closure skipped or repeated native view retirement";
        var failingView = new VeyraDepthTextureView(new VulkanTexture(), () -> {
            releases[0]++;
            throw new IllegalStateException("Retirement failed");
        });
        try { failingView.close(); throw new AssertionError("Cleanup failure was swallowed"); }
        catch (IllegalStateException expected) { }
        failingView.close();
        assert releases[0] == 3 && failingView.isClosed();
    }
    private static void checkTextureSlots() {
        var cache = new LegacyTextureSlotCache();
        var firstTexture = new VulkanTexture();
        var secondTexture = new VulkanTexture();
        var supplied = LegacyVkTextureView.create(firstTexture);
        int slot = VeyraTextureSlots.CAPACITY - 1;
        cache.bind(slot, supplied, (index, texture) -> { assert index == slot && texture == firstTexture; });
        assert cache.resolve(slot, firstTexture) == supplied;
        assert cache.resolve(0, null) == null;
        supplied.close();
        assert cache.resolve(slot, firstTexture) == supplied : "Cleanup lost the closed view's identity";
        var vanillaReplacement = cache.resolve(slot, secondTexture);
        assert vanillaReplacement != supplied && vanillaReplacement.texture() == secondTexture;
        assert cache.resolve(slot, secondTexture) == vanillaReplacement;
        assert !firstTexture.isClosed() && !secondTexture.isClosed();
        rejects(() -> cache.resolve(slot, new Texture()));
        assert cache.resolve(slot, secondTexture) == vanillaReplacement;
        assert cache.resolve(slot, null) == null;
        assert !vanillaReplacement.isClosed() && !secondTexture.isClosed();
        var restored = cache.resolve(slot, secondTexture);
        assert restored != vanillaReplacement && restored.texture() == secondTexture;
        try {
            cache.bind(slot, LegacyVkTextureView.create(firstTexture), (index, texture) -> {
                throw new IllegalStateException("Native binding failed");
            });
            throw new AssertionError("Native failure was swallowed");
        } catch (IllegalStateException expected) { }
        assert cache.resolve(slot, secondTexture) == restored;
        try {
            cache.bind(slot, supplied, (index, texture) -> { throw new AssertionError("Closed view reached native code"); });
            throw new AssertionError("Closed view was accepted");
        } catch (IllegalStateException expected) { }
        assert cache.resolve(slot, secondTexture) == restored;
        cache.bind(slot, null, (index, texture) -> { assert index == slot && texture == null; });
        assert cache.resolve(slot, null) == null && !restored.isClosed();
        for (int invalid : new int[]{-1, VeyraTextureSlots.CAPACITY, Integer.MAX_VALUE}) {
            try { cache.bind(invalid, null, (index, texture) -> { throw new AssertionError("Invalid slot reached native code"); }); throw new AssertionError("Invalid texture slot"); }
            catch (IndexOutOfBoundsException expected) { }
            try { cache.resolve(invalid, null); throw new AssertionError("Invalid texture slot"); }
            catch (IndexOutOfBoundsException expected) { }
        }
    }
    private static void checkImageDescriptorTypes() {
        var sampled = new VeyraImageDescriptorBase(4, "sampler2D", "sampled", 7,
                org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER) { };
        var storage = new VeyraImageDescriptorBase(9, "image2D", "storage", 11,
                org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE) { };
        assert sampled.getBinding() == 4 && sampled.imageIdx == 7 && sampled.name.equals("sampled");
        assert sampled.getType() == org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        assert !sampled.isStorageImage && sampled.useSampler;
        assert sampled.getLayout() == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        assert storage.getBinding() == 9 && storage.imageIdx == 11 && storage.name.equals("storage");
        assert storage.getType() == org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        assert storage.isStorageImage && !storage.useSampler;
        assert storage.getLayout() == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
        rejects(() -> new VeyraImageDescriptorBase(0, "sampler2D", "bad", 0,
                org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER) { });
    }
    private static void checkNativeUniformBlock() {
        var fields = new java.util.ArrayList<net.vulkanmod.vulkan.shader.layout.Uniform.Info>();
        for (String type : new String[]{"vec2", "vec3", "vec4", "mat4"}) {
            var field = net.vulkanmod.vulkan.shader.layout.Uniform.createUniformInfo(type, "test_" + type);
            field.setBufferSupplier(() -> null);
            fields.add(field);
        }
        var block = VeyraUniformBlocks.create("native block", 3, org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT, fields);
        assert block.getBinding() == 3 && block.getStages() == org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
        assert block.getUniformBuffer() == null : "Block lost the frame-managed global buffer";
        assert VeyraUniformBlocks.description(block).equals("UBO binding 3");
        assert block.getSize() == 112;
        var uniforms = block.getUniforms();
        assert uniforms.size() == 4;
        // Native field getters count 32-bit words; the block size counts bytes.
        assert uniforms.get(0).getOffset() == 0 && uniforms.get(0).getSize() == 2;
        assert uniforms.get(1).getOffset() == 4 && uniforms.get(1).getSize() == 3;
        assert uniforms.get(2).getOffset() == 8 && uniforms.get(2).getSize() == 4;
        assert uniforms.get(3).getOffset() == 12 && uniforms.get(3).getSize() == 16;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var sources = new java.util.ArrayList<net.vulkanmod.vulkan.util.MappedBuffer>();
            for (int index = 0; index < uniforms.size(); index++) {
                var uniform = uniforms.get(index);
                var source = net.vulkanmod.vulkan.util.MappedBuffer.createFromBuffer(stack.calloc(uniform.getSize() * 4));
                for (int word = 0; word < uniform.getSize(); word++) source.buffer.putInt(word * 4, 1000 + index * 100 + word);
                sources.add(source);
                uniform.setSupplier(() -> source);
            }
            var output = stack.calloc(block.getSize());
            block.update(org.lwjgl.system.MemoryUtil.memAddress(output));
            for (int index = 0; index < uniforms.size(); index++) {
                var uniform = uniforms.get(index);
                for (int word = 0; word < uniform.getSize(); word++) {
                    assert output.getInt((uniform.getOffset() + word) * 4) == 1000 + index * 100 + word;
                }
            }
            for (int word : new int[]{2, 3, 7}) assert output.getInt(word * 4) == 0;
            sources.getFirst().buffer.putInt(0, 123456);
            block.update(org.lwjgl.system.MemoryUtil.memAddress(output));
            assert output.getInt(0) == 123456 : "Native UBO did not refresh its supplier";
        }
    }
    private static void checkVertexUpload() throws ReflectiveOperationException {
        var switchField = Class.forName("net.vulkanmod.render.engine.VkGpuBuffer$1")
                .getDeclaredField("$SwitchMap$com$mojang$blaze3d$buffers$BufferType");
        switchField.setAccessible(true);
        int[] nativeTypes = (int[]) switchField.get(null);
        assert nativeTypes[com.mojang.blaze3d.buffers.BufferType.VERTICES.ordinal()] == 1;
        assert nativeTypes[com.mojang.blaze3d.buffers.BufferType.UNIFORM.ordinal()] == 0
                : "Native UBO support changed; revisit the legacy buffer port";
        var vertices = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(new float[]{1, 2, 3, 4, 5, 6});
        var uploaded = new com.mojang.blaze3d.buffers.GpuBuffer(
                com.mojang.blaze3d.buffers.BufferType.VERTICES, com.mojang.blaze3d.buffers.BufferUsage.STATIC_WRITE, 24) {
            private boolean closed;
            public boolean isClosed() { return closed; }
            public void close() { closed = true; }
        };
        int[] calls = {0};
        var device = (com.mojang.blaze3d.systems.GpuDevice) Proxy.newProxyInstance(
                com.mojang.blaze3d.systems.GpuDevice.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.systems.GpuDevice.class}, (proxy, method, values) -> {
                    assert method.getName().equals("createBuffer") && values.length == 4;
                    assert ((java.util.function.Supplier<?>) values[0]).get().equals("sky fixture");
                    assert values[1] == com.mojang.blaze3d.buffers.BufferType.VERTICES;
                    assert values[2] == com.mojang.blaze3d.buffers.BufferUsage.STATIC_WRITE;
                    assert values[3] == vertices && vertices.getFloat(20) == 6;
                    calls[0]++;
                    return uploaded;
                });
        assert VeyraVertexBuffers.create(device, () -> "sky fixture", vertices) == uploaded;
        assert calls[0] == 1 && vertices.position() == 0 && vertices.remaining() == 24 && !uploaded.isClosed();
    }
    private static void checkNativeSamplerFlags() {
        assert LegacyNativeSamplers.flags(false, false, 0) == 0;
        assert LegacyNativeSamplers.flags(true, false, 0) == 2;
        assert LegacyNativeSamplers.flags(false, true, 0) == 1;
        assert LegacyNativeSamplers.flags(true, true, 0) == 3;
        assert LegacyNativeSamplers.flags(false, false, 4) == 4;
        assert LegacyNativeSamplers.flags(true, false, 4) == 6;
        assert LegacyNativeSamplers.flags(false, true, 4) == 13;
        assert LegacyNativeSamplers.flags(true, true, 4) == 15;
        assert LegacyNativeSamplers.lod(0) == 0 && LegacyNativeSamplers.lod(127) == 127;
        rejects(() -> LegacyNativeSamplers.lod(-1));
        rejects(() -> LegacyNativeSamplers.lod(128));
        rejects(() -> LegacyNativeSamplers.lod(Integer.MAX_VALUE));
    }
    private static void checkDescriptorLookup() {
        var first = new net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor(2, "sampler2D", "first", 7);
        var last = new net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor(5, "image2D", "last", 11, true);
        var descriptors = java.util.List.of(first, last);
        assert LegacyImageDescriptors.find(descriptors, "first") == first;
        assert LegacyImageDescriptors.find(descriptors, "last") == last;
        assert LegacyImageDescriptors.find(descriptors, "First") == null;
        assert LegacyImageDescriptors.find(descriptors, "missing") == null;
        assert LegacyImageDescriptors.find(java.util.List.of(), "first") == null;
        assert first.getBinding() == 2 && first.imageIdx == 7 && first.useSampler;
        assert last.getBinding() == 5 && last.imageIdx == 11 && last.isStorageImage;
    }
    private static void checkTargetAllocation() {
        Texture previous = new Texture();
        Texture allocated = new Texture();
        int[] calls = {0};
        var device = (com.mojang.blaze3d.systems.GpuDevice) Proxy.newProxyInstance(
                com.mojang.blaze3d.systems.GpuDevice.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.systems.GpuDevice.class}, (proxy, method, values) -> {
                    assert method.getName().equals("createTexture");
                    assert values.length == 5 && values[0].equals("color fixture");
                    assert values[1] == TextureFormat.RGBA8;
                    assert values[2].equals(64) && values[3].equals(32) && values[4].equals(1);
                    calls[0]++;
                    return allocated;
                });
        assert VeyraTargetTextures.createColor(device, "color fixture", null, 64, 32) == allocated;
        assert VeyraTargetTextures.createColor(device, "color fixture", previous, 64, 32) == allocated;
        assert calls[0] == 2 && !previous.isClosed() && !allocated.isClosed();
        assert java.util.Arrays.stream(net.vulkanmod.render.engine.VkGpuTexture.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("needsClear"));
        var cleared = new VulkanTexture();
        VeyraTargetTextures.markCleared(cleared);
        assert !cleared.isClosed();
    }
    private static void checkRenderTargets() throws Exception {
        var target = new dev.veyra.mixin.LegacyRenderTargetMixin() {
            @Override public GpuTexture getColorTexture() { return veyra$getColorTexture(); }
        };
        var color = new VulkanTexture();
        var depth = new VulkanTexture();
        assert target.veyra$getColorTextureView() == null;
        target.veyra$setColorTexture(color);
        target.veyra$setDepthTexture(depth);
        var first = target.veyra$getColorTextureView();
        var depthView = target.veyra$getDepthTextureView();
        assert first.texture() == color && target.veyra$getColorTextureView() == first;
        assert depthView.texture() == depth && depthView != first;
        rejects(() -> target.veyra$setColorTextureView(depthView));
        assert target.veyra$getColorTextureView() == first && !first.isClosed();
        first.close();
        var recreated = target.veyra$getColorTextureView();
        assert recreated != first && !color.isClosed();
        var replacement = new VulkanTexture();
        target.veyra$setColorTexture(replacement);
        var replaced = target.veyra$getColorTextureView();
        assert recreated.isClosed() && replaced.texture() == replacement && !color.isClosed();
        var supplied = LegacyVkTextureView.create(replacement);
        target.veyra$setColorTextureView(supplied);
        assert replaced.isClosed() && target.veyra$getColorTextureView() == supplied;
        var cleanup = dev.veyra.mixin.LegacyRenderTargetMixin.class.getDeclaredMethod(
                "veyra$closeBorrowedViews", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        cleanup.setAccessible(true);
        cleanup.invoke(target, new Object[]{null});
        assert supplied.isClosed() && depthView.isClosed();
        assert !color.isClosed() && !replacement.isClosed() && !depth.isClosed();
        replacement.close();
        assert target.veyra$getColorTextureView() == null;
        target.veyra$setDepthTexture(null);
        assert target.veyra$getDepthTextureView() == null;
        var liveAttachment = new GpuTexture[]{color};
        var main = new dev.veyra.mixin.LegacyRenderTargetMixin() {
            @Override public GpuTexture getColorTexture() { return liveAttachment[0]; }
        };
        var liveView = main.veyra$getColorTextureView();
        assert liveView.texture() == color && main.veyra$getColorTexture() == null;
        liveAttachment[0] = depth;
        var nextView = main.veyra$getColorTextureView();
        assert liveView.isClosed() && nextView.texture() == depth && !color.isClosed();
        main.veyra$setColorTextureView(nextView);
        nextView.close();
    }
    private static final class VulkanTexture extends net.vulkanmod.render.engine.VkGpuTexture {
        VulkanTexture() { super("borrowed fixture", TextureFormat.RGBA8, 32, 16, 4, 0, null); }
        @Override public void close() { closed = true; }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid texture range or backend was accepted");
    }
    private static final class Texture extends GpuTexture implements LegacyGpuTextureSamplerAccessor {
        private boolean closed;
        Texture() { super("fixture", TextureFormat.RGBA8, 32, 16, 4); }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; }
        @Override public AddressMode veyra$getAddressModeU() { return addressModeU; }
        @Override public AddressMode veyra$getAddressModeV() { return addressModeV; }
        @Override public FilterMode veyra$getMinFilter() { return minFilter; }
        @Override public FilterMode veyra$getMagFilter() { return magFilter; }
        @Override public boolean veyra$getUseMipmaps() { return useMipmaps; }
    }
}
