package dev.veyra.smoke;

import com.google.gson.GsonBuilder;
import dev.veyra.mixin.accessor.NativePipelineRegistryAccessor;
import dev.veyra.smoke.mixin.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import java.nio.file.*;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Read-only registries at shutdown, excluded from the distributable mod. */
public final class ShutdownEvidence {
    private ShutdownEvidence() { }
    private static String hex(long value){return "0x"+Long.toHexString(value);}
    private static int[] sizes(ObjectArrayList<?>[] values){return Arrays.stream(values).mapToInt(List::size).toArray();}
    public static void record(String phase) {
        try {
            var memory=(GraphicsMemoryAccessor)MemoryManager.getInstance();
            List<Object> buffers=new ArrayList<>(),images=new ArrayList<>(),pipelines=new ArrayList<>();
            GraphicsMemoryAccessor.buffers().long2ReferenceEntrySet().forEach(e->{
                var b=e.getValue();
                buffers.add(Map.of("handle",hex(e.getLongKey()),"currentHandle",hex(b.getId()),
                        "name",String.valueOf(b.name),"bytes",b.getBufferSize(),"allocation",hex(b.getAllocation())));
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
            var result=Map.of("phase",phase,"buffers",buffers,"images",images,"pipelines",pipelines,
                    "frameOps",sizes(memory.frameOps()),"bufferFrees",sizes(memory.freeableBuffers()),"imageFrees",sizes(memory.freeableImages()));
            Path output=Path.of(System.getProperty("veyra.graphicsSmoke.output","graphics-smoke-output"));
            Files.createDirectories(output);
            Files.writeString(output.resolve("shutdown-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            LoggerFactory.getLogger("veyra-graphics-smoke").info("Shutdown registry: phase={} buffers={} images={} pipelines={}",phase,buffers.size(),images.size(),pipelines.size());
        } catch(Exception failure){throw new IllegalStateException("Could not record shutdown registries",failure);}
    }
}
