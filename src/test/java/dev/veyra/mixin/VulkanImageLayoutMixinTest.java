package dev.veyra.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VulkanImageLayoutMixinTest {
    @Test
    void selectsTheNativeBarrierOverloadRatherThanTheConvenienceWrapper() throws Exception {
        ClassNode mixin = read("dev/veyra/mixin/VulkanImageLayoutMixin.class");
        var handler = mixin.methods.stream().filter(method -> method.name.equals("veyra$includeLateDepthTests"))
                .findFirst().orElseThrow();
        var annotations = new ArrayList<AnnotationNode>();
        if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
        if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
        var injection = annotations.stream().filter(annotation -> annotation.desc.endsWith("/ModifyArgs;"))
                .findFirst().orElseThrow();
        List<?> selectors = (List<?>) injection.values.get(injection.values.indexOf("method") + 1);
        assertEquals(1, selectors.size());
        String selector = (String) selectors.getFirst();
        int descriptor = selector.indexOf('(');
        assertTrue(descriptor > 0, "The overloaded VulkanMod helper needs an exact JVM descriptor");

        ClassNode nativeImage = read("net/vulkanmod/vulkan/texture/VulkanImage.class");
        var targets = nativeImage.methods.stream().filter(method ->
                method.name.equals(selector.substring(0, descriptor))
                        && method.desc.equals(selector.substring(descriptor))).toList();
        assertEquals(1, targets.size(), "The selected VulkanMod ABI must exist");
        assertTrue(Arrays.stream(targets.getFirst().instructions.toArray()).anyMatch(instruction ->
                        instruction instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/vulkan/VK10")
                                && call.name.equals("vkCmdPipelineBarrier")),
                "The injection must select the barrier implementation, not its argument-shifting wrapper");
    }

    private static ClassNode read(String resource) throws Exception {
        try (var stream = VulkanImageLayoutMixinTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
