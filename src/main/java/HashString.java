import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashString {

    public static String hashByteToStringHex(byte[] message, byte[] headerBytes) {
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

    public static String hashByteToStringHex(byte[] message) {
        byte[] digest;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            // Hash header and content
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

public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
            + Character.digit(hexString.charAt(i + 1), 16));
        }

        return data;
    }

public static void hashAndSavePack(byte[] packFile, String dirName) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
        byte[] sha1 = messageDigest.digest(packFile);

        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : sha1) {
            stringBuilder.append(String.format("%02x", b));
        }


        String sha1Hex = stringBuilder.toString();

        String packFileName = "pack-" + sha1Hex + ".pack";
        Path packPath = Paths.get(dirName, ".git/objects/pack/");

        if (!packPath.toFile().exists()) {
            Files.createDirectories(packPath);
            System.out.println("Created pack directory");
        }

        try {

            Files.write(packPath.resolve(packFileName), packFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            System.out.println("Created pack file: " + packFileName);
        }   catch (FileAlreadyExistsException e) {
            System.err.println("Overwriting existing pack file..." );
            Files.write(packPath.resolve(packFileName), packFile, StandardOpenOption.TRUNCATE_EXISTING);
        }


}


}