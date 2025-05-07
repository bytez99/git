import java.io.*;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ZLibCompression {

    public byte[] decompress(File compressedFile) throws IOException {
        try(
                FileInputStream fis = new FileInputStream(compressedFile);
                InflaterInputStream iis = new InflaterInputStream(fis);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
            )
        {
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = iis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }


            return baos.toByteArray();


        }


    }

    public void compress(Path blobFilePath, byte[] headerBytes, byte[] content ) throws IOException {
        // Create our blob file with the path
        try (OutputStream fileOut = new FileOutputStream(blobFilePath.toFile());
             DeflaterOutputStream def = new DeflaterOutputStream(fileOut)) {
            // Compress our head (blob <size>\0)
            def.write(headerBytes);
            // Compress our content
            def.write(content);
        }

    }

}
