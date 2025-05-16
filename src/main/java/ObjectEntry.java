public class ObjectEntry {
    private final int type;
    private long size;
    private byte[] data;
    private boolean isDelta;


    // Delta Objects
    private String baseSha1;
    private Long baseOffset;


    // Default Object
    public ObjectEntry(int objectType, long objectSize, byte[] data) {
        this.type = objectType;
        this.size = objectSize;
        this.data = data;
        this.isDelta = false;

    }

    // Ref Delta Objects
    public ObjectEntry(int type, long size, byte[]data,  String baseSha1 ) {
        this(type, size, data);
        this.baseSha1 = baseSha1;
        this.isDelta = true;
    }

    // Ofs Delta Objects
    public ObjectEntry(int type, long size, byte[]data,  Long baseOffset ) {
        this(type, size, data);
        this.baseOffset = baseOffset;
        this.isDelta = true;
    }

    public boolean isDeltaObject() {
        return type == 6 || type == 7;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getBaseSha1() {
        return baseSha1;
    }

    public void setBaseSha1(String baseSha1) {
        this.baseSha1 = baseSha1;
    }

    public Long getBaseOffset() {
        return baseOffset;
    }

    public void setBaseOffset(Long baseOffset) {
        this.baseOffset = baseOffset;
    }
}
