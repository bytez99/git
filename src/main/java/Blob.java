import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Blob {
    private String blobHash;

    public String readBlob(String dir, String filename) throws IOException {
        File file = new File(".git/" + "objects/" + dir + "/" + filename);

        if (!file.exists()) {
            System.err.println("File does not exist: " + file);
        }

        byte[] objectDecompressed = ZLibCompression.decompress(file);
        ByteArrayInputStream byteArrInputStream = new ByteArrayInputStream(objectDecompressed);

        ByteArrayOutputStream byteArrOut = new ByteArrayOutputStream();



        int b;
        while ((b = byteArrInputStream.read()) != -1) {
            if (b == 0) break;

        }

        while ((b = byteArrInputStream.read()) != -1) {
            byteArrOut.write(b);
        }

        return byteArrOut.toString();

    }

    public void createBlob(String file) throws IOException {

        byte[] content = null;

        try {
            content = Files.readAllBytes(Paths.get(file));
        }catch (IOException e){
            System.err.println("Error reading file: " + file + ": " + e.getMessage());
        }

        String header = "blob " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

        setBlobHash(HashString.hashByteToStringHex(content, headerBytes));

        String blobFolder = getBlobHash().substring(0, 2);
        String blobFileName = getBlobHash().substring(2);

        Path blobPath = Paths.get(".git", "objects", blobFolder);
        Files.createDirectories(blobPath);

        Path blobFolderPath = blobPath.resolve(blobFileName);

        ZLibCompression.compress(blobFolderPath, headerBytes, content);

    }


    public String getBlobHash() {
        return blobHash;
    }
    public void setBlobHash(String blobHash) {
        this.blobHash = blobHash;
    }


}
