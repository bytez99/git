import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Tree {




    public List<TreeEntry> readTree(String treeSha) throws IOException {
        String treeFolder = treeSha.substring(0, 2);
        String treeName = treeSha.substring(2);
        File file = new File(".git/" + "objects/" + treeFolder + "/" + treeName);

        if (!file.exists()) {
            System.err.println("File does not exist: " + file.getAbsolutePath());
        }

        ZLibCompression z = new ZLibCompression();

        byte[] treeBytes = z.decompress(file);


        return treeParser(treeBytes);
    }

    public static List<TreeEntry> treeParser(byte[] data) throws IOException {
        List<TreeEntry> entries = new ArrayList<>();
        ByteArrayInputStream byteArrInputStream = new ByteArrayInputStream(data);

        ByteArrayOutputStream byteArrOut = new ByteArrayOutputStream();
        int b;

        // Read header first
        while ((b = byteArrInputStream.read()) != -0) {
            byteArrOut.write(b);
        }

        String header = byteArrOut.toString();
        String headerType = header.substring(0, header.indexOf(" "));
        int headerLength = Integer.parseInt(header.substring(header.indexOf(" ") + 1));


        // Read rest until we reach end of OutPutStream
        while (byteArrInputStream.available() > 0) {
            ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();


            // Reset output string to write the rest
            byteArrayOut.reset();
            while ((b = byteArrInputStream.read()) != 0) {
                byteArrayOut.write(b);
            }

            String modeAndName = byteArrayOut.toString();
            int modeAndNameEndIndex = modeAndName.indexOf(" ");
            String mode = modeAndName.substring(0, modeAndNameEndIndex);
            String name = modeAndName.substring(modeAndNameEndIndex + 1);
            // Finally read 20 byte sha
            byte[] sha = new byte[20];
            int read = byteArrInputStream.read(sha);
            if (read != 20) throw new IOException("Failed to read 2-byte SHA");

            entries.add(new TreeEntry(headerType, headerLength, mode, name, sha));

        }

        return entries;
    }


}
