import java.io.*;
import java.nio.file.Path;
import java.util.zip.*;

public class ZLibCompression {

    public static byte[] decompress(File compressedFile) throws IOException {
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

    public static void compress(Path blobFilePath, byte[] headerBytes, byte[] content ) throws IOException {
        // Create our blob file with the path
        try (OutputStream fileOut = new FileOutputStream(blobFilePath.toFile());
             DeflaterOutputStream def = new DeflaterOutputStream(fileOut)) {
            // Compress our head (blob <size>\0)
            def.write(headerBytes);
            // Compress our content
            def.write(content);
        }

    }


    public static byte[] decompressNextZlibBlock(PushbackInputStream in) throws IOException {
        Inflater inflater = new Inflater();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] inputBuffer = new byte[512];
        byte[] outputBuffer = new byte[4096];

        boolean done = false;

        while (!done) {
            int bytesRead = in.read(inputBuffer);
            if (bytesRead == -1) {
                throw new IOException("Unexpected end of stream during zlib decompression");
            }

            inflater.setInput(inputBuffer, 0, bytesRead);

            try {
                while (!inflater.finished()) {
                    int count = inflater.inflate(outputBuffer);
                    if (count > 0) {
                        out.write(outputBuffer, 0, count);
                    } else if (inflater.needsInput()) {
                        // Need more compressed data, break inner loop to read more
                        break;
                    } else if (count == 0) {
                        if (inflater.finished()) {
                            done = true;
                            break;
                        } else if (inflater.needsInput()) {
                            break;
                        } else {
                            throw new IOException("Inflater stalled without finishing or needing input");
                        }
                    }
                }
            } catch (DataFormatException e) {
                throw new IOException("Zlib decompression failed: corrupted or misaligned data", e);
            }

            if (inflater.finished()) {
                done = true;

                // Calculate how many input bytes remain unused
                int remaining = inflater.getRemaining();

                // Push back unused bytes so next read starts correctly
                if (remaining > 0) {
                    int pushbackStart = bytesRead - remaining;
                    in.unread(inputBuffer, pushbackStart, remaining);
                }
            }
        }

        inflater.end();
        return out.toByteArray();
    }


}