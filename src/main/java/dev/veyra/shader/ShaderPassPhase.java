package dev.veyra.shader;

/** Iris pass boundaries; final is part of the last phase after composite. */
public enum ShaderPassPhase {
    PREPARE, DEFERRED, COMPOSITE;

    public static ShaderPassPhase forProgram(String name) {
        if (name.matches("prepare\\d*")) return PREPARE;
        if (name.matches("deferred\\d*")) return DEFERRED;
        if (name.matches("composite\\d*") || name.equals("final")) return COMPOSITE;
        throw new IllegalArgumentException("Not a post-processing program: " + name);
    }
}
