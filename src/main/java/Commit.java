import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class Commit {
    private final String author = "author";
    private final String authorEmail = "author@email.com";
    private final String committer = "committer";
    private final String committerEmail = "committer@email.com";
    private Instant time;
    private ZLibCompression compression = new ZLibCompression();
    private HashString hash = new HashString();
    private String commitHash;


    public void buildInitialCommitContent(String treeSha, String commitMessage) throws IOException {
        setTime(Instant.now());

        long timeStamp = getTimeStamp();
        String timeOffset = getTimeOffset();

        String content = "tree " + treeSha + "\n"
                + "author " + author + " <" + authorEmail + "> " + timeStamp + " "  + timeOffset  +"\n"
                + "committer " + committer + " <" + committerEmail + "> " + timeStamp + " "  + timeOffset  + "\n\n"
                + commitMessage + "\n";

        createCommit(content);

    }

    public void buildCommitContent(String treeSha, String parentTreeSha, String commitMessage) throws IOException {
        setTime(Instant.now());

        long timeStamp = getTimeStamp();
        String timeOffset = getTimeOffset();

        String content = "tree " + treeSha + "\n"
                + "parent " + parentTreeSha + "\n"
                + "author " + author + " <" + authorEmail + "> " + timeStamp + " "  + timeOffset  +"\n"
                + "committer " + committer + " <" + committerEmail + "> " + timeStamp + " " + timeOffset  + "\n\n"
                + commitMessage;

        createCommit(content);

    }

    public void createCommit(String content) throws IOException {

        String header = "commit " + content.length() + "\0";
        byte[] headerBytes = header.getBytes();
        byte[] contentBytes = content.getBytes();

        String commitHash = hash.hashByteToStringHex(contentBytes, headerBytes);

        setCommitHash(commitHash);

        File objectFolder = new File(".git/objects/" + commitHash.substring(0, 2));

        if (!objectFolder.exists()) {
            objectFolder.mkdir();
        }

        File objectFile = new File(objectFolder + "/" + commitHash.substring(2));

        Path objectPath = objectFile.toPath();

        compression.compress(objectPath, headerBytes, contentBytes);

    }


public String getAuthor() {
        return author;
}

public String getAuthorEmail() {
        return authorEmail;
}

public long getTimeStamp() {
        return time.getEpochSecond();
}

public void setTime(Instant time) {
        this.time = time;
}

public String getTimeOffset(){
        ZonedDateTime zonedDateTime = time.atZone(ZoneOffset.systemDefault());
        ZoneOffset offset = zonedDateTime.getOffset();

        return offset.getId().replace("Z", "+0000").replace(":", "");

}

public String getCommitHash(){
        return commitHash;
}

public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
}



}
