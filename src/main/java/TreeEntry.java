public class TreeEntry {
    private String headerType;
    private String mode;
    private String name;
    private byte[] sha;

    public TreeEntry(String mode, String headerType, String name, byte[] sha) {
        this.mode = mode;
        this.headerType = headerType;
        this.name = name;
        this.sha = sha;

    }

    public TreeEntry(String mode, String name, byte[] sha) {
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


    public String getName() {
        return name;
    }



    public String getMode() {
        return mode;
    }


    public String getHeaderType() {
        return headerType;
    }



}
