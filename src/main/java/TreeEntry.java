public class TreeEntry {
    int headerSize;
    String headerType;
    String mode;
    String name;
    byte[] sha;

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
}