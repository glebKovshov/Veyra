package dev.veyra.shader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComputeProgramSequenceTest {
    @Test
    void computeComponentsRunAtTheirParentPostProgramBoundary() {
        var events = ComputeProgramSequence.interleave(ShaderPassPhase.COMPOSITE,
                List.of("composite1", "composite", "final"),
                List.of("composite", "composite1", "final"));

        assertEquals(List.of(
                ComputeProgramSequence.Event.compute("composite"),
                ComputeProgramSequence.Event.graphics("composite", 0),
                ComputeProgramSequence.Event.compute("composite1"),
                ComputeProgramSequence.Event.graphics("composite1", 1),
                ComputeProgramSequence.Event.compute("final"),
                ComputeProgramSequence.Event.graphics("final", 2)), events);
    }

    @Test
    void computeOnlyPassesStayInNumericPassOrder() {
        var events = ComputeProgramSequence.interleave(ShaderPassPhase.DEFERRED,
                List.of("deferred10", "deferred", "deferred2"),
                List.of("deferred", "deferred2", "deferred10"));

        assertEquals(List.of(
                ComputeProgramSequence.Event.compute("deferred"),
                ComputeProgramSequence.Event.graphics("deferred", 0),
                ComputeProgramSequence.Event.compute("deferred2"),
                ComputeProgramSequence.Event.graphics("deferred2", 1),
                ComputeProgramSequence.Event.compute("deferred10"),
                ComputeProgramSequence.Event.graphics("deferred10", 2)), events);
    }

    @Test
    void computeProgramWithoutFragmentPassRunsBeforeTheNextPostProgram() {
        var events = ComputeProgramSequence.interleave(ShaderPassPhase.PREPARE,
                List.of("prepare2"), List.of("prepare", "prepare3"));

        assertEquals(List.of(
                ComputeProgramSequence.Event.graphics("prepare", 0),
                ComputeProgramSequence.Event.compute("prepare2"),
                ComputeProgramSequence.Event.graphics("prepare3", 1)), events);
    }

    @Test
    void eachPostPhaseKeepsComputeOnlyPassesInPhaseOrder() {
        assertEquals(List.of(ComputeProgramSequence.Event.compute("prepare")),
                ComputeProgramSequence.interleave(ShaderPassPhase.PREPARE, List.of("prepare"), List.of()));
        assertEquals(List.of(ComputeProgramSequence.Event.compute("deferred")),
                ComputeProgramSequence.interleave(ShaderPassPhase.DEFERRED, List.of("deferred"), List.of()));
        assertEquals(List.of(ComputeProgramSequence.Event.compute("final")),
                ComputeProgramSequence.interleave(ShaderPassPhase.COMPOSITE, List.of("final"), List.of()));
    }
}
