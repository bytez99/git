import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashString {
    public String encryptString(byte[] message, byte[] headerBytes) throws NoSuchAlgorithmException {



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

}