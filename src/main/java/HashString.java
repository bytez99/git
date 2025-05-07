import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashString {

    public String hashByteToStringHex(byte[] message, byte[] headerBytes) {
        byte[] digest;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            // Hash header and content
            messageDigest.update(headerBytes);
            messageDigest.update(message);
            digest = messageDigest.digest();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : digest) {
            stringBuilder.append(String.format("%02x", b));
        }

        return stringBuilder.toString();

    }

public byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
            + Character.digit(hexString.charAt(i + 1), 16));
        }

        return data;
    }

}