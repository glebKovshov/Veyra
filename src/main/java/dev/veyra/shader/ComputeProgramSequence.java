package dev.veyra.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Merges compute and fragment programs at the pass boundaries used by Iris. */
final class ComputeProgramSequence {
    enum Kind { COMPUTE, GRAPHICS }

    record Event(Kind kind, String programName, int graphicsIndex) {
        static Event compute(String name) { return new Event(Kind.COMPUTE, name, -1); }
        static Event graphics(String name, int index) { return new Event(Kind.GRAPHICS, name, index); }
    }

    private ComputeProgramSequence() { }

    static List<Event> interleave(ShaderPassPhase phase, List<String> computePrograms,
                                  List<String> graphicsPrograms) {
        List<String> computes = computePrograms.stream().distinct()
                .filter(name -> ShaderPassPhase.forProgram(name) == phase)
                .sorted(programOrder(phase)).toList();
        List<String> graphics = graphicsPrograms.stream()
                .filter(name -> ShaderPassPhase.forProgram(name) == phase).toList();
        List<Event> events = new ArrayList<>(computes.size() + graphics.size());
        int computeIndex = 0;
        int graphicsIndex = 0;
        while (computeIndex < computes.size() || graphicsIndex < graphics.size()) {
            if (graphicsIndex == graphics.size()
                    || computeIndex < computes.size()
                    && programOrder(phase).compare(computes.get(computeIndex), graphics.get(graphicsIndex)) <= 0) {
                events.add(Event.compute(computes.get(computeIndex++)));
            } else {
                events.add(Event.graphics(graphics.get(graphicsIndex), graphicsIndex++));
            }
        }
        return List.copyOf(events);
    }

    private static Comparator<String> programOrder(ShaderPassPhase phase) {
        return (first, second) -> {
            if (phase == ShaderPassPhase.COMPOSITE) {
                if (first.equals("final")) return second.equals("final") ? 0 : 1;
                if (second.equals("final")) return -1;
            }
            String prefix = switch (phase) {
                case PREPARE -> "prepare";
                case DEFERRED -> "deferred";
                case COMPOSITE -> "composite";
            };
            return Integer.compare(passIndex(prefix, first), passIndex(prefix, second));
        };
    }

    private static int passIndex(String prefix, String name) {
        if (name.equals(prefix)) return 0;
        return Integer.parseInt(name.substring(prefix.length()));
    }
}
