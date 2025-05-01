import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;


public class Main {
    public static void main(String[] args) throws IOException {

        System.err.println("Logs from your program will appear here!");

        final String command = args[0];

        switch (command) {

            case "init" -> {

                final File root = new File(".git");
                new File(root, "objects").mkdirs();
                new File(root, "refs").mkdirs();
                final File head = new File(root, "HEAD");

                try {
                    head.createNewFile();
                    Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());
                    System.out.println("Initialized git directory");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            case "cat-file" -> {

                if (args.length < 3) {
                    System.err.println("Usage: cat-file -p <object-sha>");
                }
                String flag = args[1];
                String objectSha = args[2];

                if (!flag.equals("-p")) {
                    System.err.println("Unsupported flag: " + flag);
                }

                // Get file folder/file name
                String dir = objectSha.substring(0, 2);
                String filename = objectSha.substring(2);


                File file = new File(".git/" + "objects/" + dir + "/" + filename);


                if (!file.exists()) {
                    System.err.println("File does not exist: " + file);
                }

                ZLibCompression z = new ZLibCompression();

                // Store our decompressed file as an array of bytes
                byte[] objectDecompressed = z.decompress(file);

                int i = 0;
                // Find null byte (0)
                while (i < objectDecompressed.length && objectDecompressed[i] != 0) {
                    i++;

                }

                // Skip to value after null value
                i ++;


                // Return objectContent after null value
                byte[] contentBytes = new byte[objectDecompressed.length - i];
                System.arraycopy(objectDecompressed, i, contentBytes, 0, contentBytes.length);

                // Convert byte array to String
                String objectContent = new String(contentBytes, StandardCharsets.UTF_8);


                System.out.print(objectContent);
            }

            case "hash-object" -> {

                String flag = args[1];
                String fileDir = args[2];

                // Check if curr dir has .git folder and objects folder

                File gitFolder = new File(".git");

                if (!gitFolder.exists()) {
                    System.err.println("No such git folder. Please initialise git with init or open a valid directory.");
                }

                if (args.length < 3) {
                    System.err.println("Usage: hash-object -w <file>");
                }

                if (!flag.equals("-w")) {
                    System.err.println("Unsupported flag: " + flag);
                }

                byte[] content;

                try {
                    // Read out file as an array of bytes
                     content = Files.readAllBytes(Paths.get(fileDir));
                }catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Create our header file with the content.length of the size in bytes
                String header = "blob " + content.length + "\0";

                // Convert our header file to raw bytes encoded as utf-8
                byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);


                HashString hash = new HashString();

                String hashString = null;
                try {
                    // Hash our string with SHA-1
                    hashString = hash.encryptString(content, headerBytes);
                } catch (NoSuchAlgorithmException e) {
                    System.err.println(e);
                }

                // Our blob dir/file name
                String blobFolder = hashString.substring(0, 2);
                String blobFileName = hashString.substring(2);

                // get path to our .git folder then appends objects folder
                Path objectsDir = gitFolder.toPath().resolve("objects");

                // append our blob folder inside our objects folder
                Path newBlobDir = objectsDir.resolve(blobFolder);

                // creates our new dir to our blob dir
                Files.createDirectories(newBlobDir);

                // creates new dir to our new blob dir -> blob fileName
                Path completeBlobPath = newBlobDir.resolve(blobFileName);


                ZLibCompression z = new ZLibCompression();
                // Finally compress our head/content
                z.compress(completeBlobPath, headerBytes, content);
                // Print out hash
                System.out.print(hashString);


            }


            default -> System.out.println("Unknown command: " + command);
        }
    }
}
