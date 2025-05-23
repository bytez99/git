public class ObjectEntry {
    private final int type;
    private long size;
    private byte[] data;
    private boolean isDelta;


    // Delta Objects
    private int actualType;
    private String baseSha1;
    private Long baseOffset;


    // Default Object
    public ObjectEntry(int objectType, long objectSize, byte[] data) {
        this.type = objectType;
        this.size = objectSize;
        this.data = data;
        this.isDelta = false;

    }

    // Ref Delta Objects Type 7
    public ObjectEntry(int type, long size, byte[]data,  String baseSha1) {
        this(type, size, data);
        this.baseSha1 = baseSha1;
        this.isDelta = true;
    }

    // Ofs Delta Objects Type 6
    public ObjectEntry(int type, long size, byte[]data,  Long baseOffset ) {
        this(type, size, data);
        this.baseOffset = baseOffset;
        this.isDelta = true;
    }

    public byte[] getData() {
        return data;
    }

    public String getBaseSha1() {
        return baseSha1;
    }

    public Long getBaseOffset() {
        return baseOffset;
    }

    public int getType() {
        return type;
    }

    public void setActualType(int actualType) {
        this.actualType = actualType;
    }
}
