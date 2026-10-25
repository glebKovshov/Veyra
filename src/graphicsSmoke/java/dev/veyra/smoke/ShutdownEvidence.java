package dev.veyra.smoke;

import com.google.gson.GsonBuilder;
import dev.veyra.mixin.accessor.NativePipelineRegistryAccessor;
import dev.veyra.shader.VeyraStorageBufferResources;
import dev.veyra.smoke.mixin.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import java.nio.file.*;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Read-only registries at shutdown, excluded from the distributable mod. */
public final class ShutdownEvidence {
    private static boolean cleanupStarted;
    private static int cleanupFlushes;
    public static void beginCleanup() { cleanupStarted = true; }
    public static void recordCleanupFlush() { if (cleanupStarted) record("after-freeAllBuffers-" + (++cleanupFlushes)); }
    private ShutdownEvidence() { }
    private static String hex(long value){return "0x"+Long.toHexString(value);}
    private static int[] sizes(ObjectArrayList<?>[] values){return Arrays.stream(values).mapToInt(List::size).toArray();}
    public static void record(String phase) {
        try {
            var memory=(GraphicsMemoryAccessor)MemoryManager.getInstance();
            List<Object> buffers=new ArrayList<>(),shaderPackBuffers=new ArrayList<>(),images=new ArrayList<>(),pipelines=new ArrayList<>();
            GraphicsMemoryAccessor.buffers().long2ReferenceEntrySet().forEach(e->{
                var b=e.getValue();
                Object entry=Map.of("handle",hex(e.getLongKey()),"currentHandle",hex(b.getId()),
                        "name",String.valueOf(b.name),"bytes",b.getBufferSize(),"allocation",hex(b.getAllocation()));
                buffers.add(entry);
                if (String.valueOf(b.name).startsWith("Veyra bufferObject.")) shaderPackBuffers.add(entry);
            });
            GraphicsMemoryAccessor.images().long2ReferenceEntrySet().forEach(e->{
                var i=e.getValue(); images.add(Map.of("handle",hex(e.getLongKey()),"name",String.valueOf(i.name),
                        "width",i.width,"height",i.height,"format",i.format));
            });
            for(var p:List.copyOf(NativePipelineRegistryAccessor.veyra$getPipelines())) {
                var access=(GraphicsNativePipelineAccessor)p;
                List<Object> pools=new ArrayList<>();
                if(access.descriptorSets()!=null) for(var d:access.descriptorSets()) {
                    var pool=(GraphicsDescriptorPoolAccessor)d;
                    long[] sets=pool.sets();
                    pools.add(Map.of("pool",hex(pool.descriptorPool()),"sets",sets==null?List.of():Arrays.stream(sets).filter(v->v!=0).mapToObj(ShutdownEvidence::hex).toList()));
                }
                List<String> variants=p instanceof GraphicsPipeline graphics
                        ? ((GraphicsVariantsAccessor)graphics).variants().values().longStream().mapToObj(ShutdownEvidence::hex).toList():List.of();
                pipelines.add(Map.of("name",String.valueOf(p.name),"layout",hex(access.pipelineLayout()),"descriptorLayout",hex(access.descriptorSetLayout()),"pools",pools,"variants",variants));
            }
            VeyraStorageBufferResources storageResources=VeyraStorageBufferResources.active();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("phase",phase); result.put("buffers",buffers); result.put("shaderPackBuffers",shaderPackBuffers);
            result.put("images",images); result.put("pipelines",pipelines);
            result.put("storageBufferResourcesActive",storageResources!=null);
            result.put("storageBufferResourcesIdentity",storageResources==null?0:storageResources.identity());
            result.put("storageBufferResourcesBytes",storageResources==null?0:storageResources.allocatedBytes());
            result.put("frameOps",sizes(memory.frameOps())); result.put("bufferFrees",sizes(memory.freeableBuffers()));
            result.put("imageFrees",sizes(memory.freeableImages()));
            Path output=Path.of(System.getProperty("veyra.graphicsSmoke.output","graphics-smoke-output"));
            Files.createDirectories(output);
            Files.writeString(output.resolve("shutdown-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            LoggerFactory.getLogger("veyra-graphics-smoke").info(
                    "Shutdown registry: phase={} buffers={} shaderPackBuffers={} images={} pipelines={} activeStorageBuffers={}",
                    phase,buffers.size(),shaderPackBuffers.size(),images.size(),pipelines.size(),storageResources!=null);
        } catch(Exception failure){throw new IllegalStateException("Could not record shutdown registries",failure);}
    }
}
