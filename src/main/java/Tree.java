import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Tree {
    public ZLibCompression libCompression = new ZLibCompression();
    public HashString hash = new HashString();


    public byte[] buildTreeContent(List<TreeEntry> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (TreeEntry entry : entries) {


            String entryLine = String.format("%s %s", entry.getMode(), entry.getName());
            baos.write(entryLine.getBytes(StandardCharsets.UTF_8));
            baos.write(0);
            baos.write(entry.getSha());
        }

       return baos.toByteArray();
    }


    public String writeTree(File root) throws IOException {
        List<TreeEntry> entries = new ArrayList<>();


        for (File file : Objects.requireNonNull(root.listFiles())){

            if (file.isFile()){
                String blobHash = writeBlob(file);
                entries.add(new TreeEntry("100644", file.getName(), hash.hexStringToByteArray(blobHash)));

            } else if (file.isDirectory() && !file.getName().equals(".git")) {
                String treeHash = writeTree(file);
                entries.add(new TreeEntry("40000", file.getName(), hash.hexStringToByteArray(treeHash)));


            }

        }

        byte[] treeContent = buildTreeContent(entries);
        String treeHeader =  "tree " + treeContent.length + "\0";
        byte[] treeHeaderBytes = treeHeader.getBytes(StandardCharsets.UTF_8);
        String treeHash = hash.hashByteToStringHex(treeContent, treeHeaderBytes);

        Path treePath = Paths.get(".git", "objects", treeHash.substring(0, 2));
        Files.createDirectories(treePath);
        Path completeTreePath = treePath.resolve(treeHash.substring(2));

        libCompression.compress(completeTreePath, treeHeaderBytes, treeContent);

        return treeHash;
    }

    public String writeBlob(File file) throws IOException {

        String stringFile = file.toString();

        Blob blob = new Blob();
        blob.createBlob(stringFile);

        return blob.getBlobHash();
    }


    public String createTree() throws IOException {
        File root = new File(".");

        return writeTree(root);
    }


    public List<TreeEntry> readTree(String treeSha) throws IOException {
        String treeFolder = treeSha.substring(0, 2);
        String treeName = treeSha.substring(2);
        File file = new File(".git/" + "objects/" + treeFolder + "/" + treeName);

        if (!file.exists()) {
            System.err.println("File does not exist: " + file.getAbsolutePath());
        }

        //ZLibCompression z = new ZLibCompression();
        byte[] treeBytes = libCompression.decompress(file);

        return treeParser(treeBytes);
    }

    public static List<TreeEntry> treeParser(byte[] data) throws IOException {
        List<TreeEntry> entries = new ArrayList<>();
        ByteArrayInputStream byteArrInputStream = new ByteArrayInputStream(data);

        ByteArrayOutputStream byteArrOut = new ByteArrayOutputStream();
        int b;

        // Skip first Header
        while ((b = byteArrInputStream.read()) != 0) {
            byteArrOut.write(b);
        }

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

            byte[] sha = new byte[20];
            int read = byteArrInputStream.read(sha);
            if (read != 20) throw new IOException("Failed to read 20-byte SHA");

            String type = mode.equals("40000") ? "tree" : "blob";

            entries.add(new TreeEntry(mode, type, name, sha));

        }

        return entries;
    }

}
