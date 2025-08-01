import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;


public class Main {
    public static void main(String[] args) throws Exception {

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

                Blob blob = new Blob();
                // Get file folder/file name
                String dir = objectSha.substring(0, 2);
                String filename = objectSha.substring(2);

                String blobContent = blob.readBlob(dir, filename);

                System.out.print(blobContent);

            }

            case "hash-object" -> {
                if (args.length < 3) {
                    System.err.println("Usage: hash-object -w <file>");
                }

                String flag = args[1];
                String fileDir = args[2];

                // Check if curr dir has .git folder and objects folder

                File gitFolder = new File(".git");

                if (!gitFolder.exists()) {
                    System.err.println("No such git folder. Please initialise git with init or open a valid directory.");
                }



                if (!flag.equals("-w")) {
                    System.err.println("Unsupported flag: " + flag);
                }


                Blob blob = new Blob();
                blob.createBlob(fileDir);

                System.out.print(blob.getBlobHash());


            }

            case "ls-tree" -> {

                if(args.length < 2 || args.length > 3) {
                    System.err.println("Usage: ls-tree <optional flag> <object-sha>");
                }

                String flag;
                String treeSha;

                if (args.length == 2) {
                    treeSha = args[1];

                }else {
                    flag = args[1];
                    treeSha = args[2];
                    if(!flag.equals("--name-only")) {
                        System.err.println("Unsupported flag: " + flag);
                    }
                }


                Tree tree = new Tree();
                List<TreeEntry> treeEntries = tree.readTree(treeSha);

                treeEntries.sort(Comparator.comparing(TreeEntry::getName));


                if (args.length == 3) {

                    for(TreeEntry entry : treeEntries) {

                    System.out.print(entry.getName());
                    }

                }else {
                    for (TreeEntry entry : treeEntries) {
                        System.out.print(entry.getMode());
                        System.out.print(" " + entry.getHeaderType());
                        System.out.print(" " + entry.toHexString(entry.getSha()).toLowerCase());
                        System.out.print("    " + entry.getName() + "\n");

                    }
                }

            }

            case "write-tree" -> {

                Tree tree = new Tree();
                System.out.print(tree.createTree());


            }

            case "commit-tree" -> {
                Commit commit = new Commit();

                if (args.length < 4) {
                    System.err.println("Usage: commit-tree <object-sha> -m <commit-message>");
                }


                String treeSha = args[1];
                String commitMessage = args[3];

                if (args.length > 6) {
                    System.err.println("Too many arguments.");
                }
                if (args.length == 6) {
                    String parentTreeSha = args[3];
                    commitMessage = args[5];
                    commit.buildCommitContent(treeSha, parentTreeSha, commitMessage);

                }else {
                    commit.buildInitialCommitContent(treeSha, commitMessage);
                }

                System.out.print(commit.getCommitHash());


            }

            case "clone" ->{
                Clone clone = new Clone();
                if (args.length < 2) {
                    System.err.println("Usage: clone <git-url> <dir-name>");
                }

                if (args.length == 2) {
                    clone.cloneRepo(args[1]);
                }

                if (args.length > 3) {
                    System.err.println("Too many arguments. Usage: clone <git-url> <dir-name>");
                }
                clone.cloneRepo(args[1], args[2]);





            }


            default -> System.out.println("Unknown command: " + command);
        }
    }
}
