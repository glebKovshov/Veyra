package dev.veyra.screen;

public final class ShaderPackListEntryCheck {
    public static void main(String[] args) {
        assert !ShaderPackListEntry.isDoubleClick(-1, 0);
        assert ShaderPackListEntry.isDoubleClick(1000, 1249);
        assert !ShaderPackListEntry.isDoubleClick(1000, 1250);
        assert !ShaderPackListEntry.isDoubleClick(1000, 999);
        System.out.println("Legacy pack-list click timing checks passed");
    }
}
