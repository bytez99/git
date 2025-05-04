public class TreeEntry {
    private int headerSize;
    private String headerType;
    private String mode;



    private String name;
    private byte[] sha;

    public TreeEntry(String headerType, int headerSize, String mode, String name, byte[] sha) {
        this.headerType = headerType;
        this.headerSize = headerSize;
        this.mode = mode;
        this.name = name;
        this.sha = sha;
    }

    public String toHexString(byte[] data) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : data){
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    public byte[] getSha() {
        return sha;
    }

    public void setSha(byte[] sha) {
        this.sha = sha;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHeaderType() {
        return headerType;
    }

    public void setHeaderType(String headerType) {
        this.headerType = headerType;
    }

    public int getHeaderSize() {
        return headerSize;
    }

    public void setHeaderSize(int headerSize) {
        this.headerSize = headerSize;
    }
}