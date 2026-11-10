package dev.veyra.compat;

/** Stand-in for Minecraft 1.21.5's texture handle; 1.21.4 identifies textures by OpenGL-style ids. */
public abstract class GpuTexture implements AutoCloseable {
    private final String label;
    private final TextureFormat format;
    private final int width;
    private final int height;
    private final int mipLevels;
    protected AddressMode addressModeU = AddressMode.REPEAT;
    protected AddressMode addressModeV = AddressMode.REPEAT;
    protected FilterMode minFilter = FilterMode.NEAREST;
    protected FilterMode magFilter = FilterMode.LINEAR;
    protected boolean useMipmaps = true;

    protected GpuTexture(String label, TextureFormat format, int width, int height, int mipLevels) {
        this.label = label;
        this.format = format;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
    }

    public String getLabel() { return label; }
    public TextureFormat getFormat() { return format; }
    public int getWidth(int mip) { return Math.max(1, width >> mip); }
    public int getHeight(int mip) { return Math.max(1, height >> mip); }
    public int getMipLevels() { return mipLevels; }
    public AddressMode addressModeU() { return addressModeU; }
    public AddressMode addressModeV() { return addressModeV; }
    public FilterMode minFilter() { return minFilter; }
    public FilterMode magFilter() { return magFilter; }
    public boolean useMipmaps() { return useMipmaps; }

    public void setAddressMode(AddressMode mode) { setAddressMode(mode, mode); }

    public void setAddressMode(AddressMode u, AddressMode v) {
        addressModeU = u;
        addressModeV = v;
    }

    public void setTextureFilter(FilterMode filter, boolean mipmaps) { setTextureFilter(filter, filter, mipmaps); }

    public void setTextureFilter(FilterMode min, FilterMode mag, boolean mipmaps) {
        minFilter = min;
        magFilter = mag;
        useMipmaps = mipmaps;
    }

    public abstract boolean isClosed();
    @Override public abstract void close();
}
