import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class Clone {

    private static String gitUrl;
    private Map<String, ObjectEntry> objectEntries = new HashMap<>();
    private List<ObjectEntry> pendingDeltas = new ArrayList<>();
    private Map<Long, ObjectEntry> offsetToEntry = new HashMap<>();
    private String headReference;
    private String dirName;


    public void cloneRepo(String gitUrl, String dirName) throws IOException, InterruptedException {
        setGitUrl(gitUrl);
        setDirName(dirName);
        referenceDiscovery(gitUrl);
    }

    public void cloneRepo(String gitUrl) throws IOException, InterruptedException {
        setGitUrl(gitUrl);
        setDirName(".");
        referenceDiscovery(gitUrl);
    }

    private void referenceDiscovery(String gitUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gitUrl + "/info/refs?service=" + "git-upload-pack"))

                .GET()
                .setHeader("Cache-Control", "no-cache")
                .setHeader("Accept", "application/x-git-upload-pack-advertisement")
                .version(HttpClient.Version.HTTP_1_1)
                .build();


        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.statusCode());
        System.out.println(response.body());
        System.out.println("\n");


        if (validateResponse(response)){

            parseSmartReply(response.body());
        }

    }

    private boolean validateResponse(HttpResponse<String> response) {

        final Pattern GIT_SERVICE_PREFIX = Pattern.compile("^[0-9a-f]{4}#");
        if (response.statusCode() != 200 && response.statusCode() != 304) {
            System.err.println(response.statusCode() + " " + response.body());
            return false;
        }
        String bodyPrefix = response.body().substring(0, 5);
        if (!GIT_SERVICE_PREFIX.matcher(bodyPrefix).matches()) {
            System.err.println("Invalid Git service prefix: " + bodyPrefix);
            return false;
        }
        String serviceString = response.body().substring(response.body().indexOf("=") + 1, response.body().indexOf("\n"));

        if (!serviceString.equals("git-upload-pack")) {
            System.err.println("Invalid Git service string: " + serviceString);
            return false;
        }
        String lastLine = response.body().substring(response.body().length() - 4);
        if (!lastLine.equals("0000")){
            System.err.println("Invalid last line: " + lastLine);
            return false;
        }
        return true;
    }

    private void parseSmartReply(String responseBody) throws IOException, InterruptedException {

        int startReferenceIdIndex = responseBody.indexOf("\n") + 9;
        String referenceId = responseBody.substring(startReferenceIdIndex, startReferenceIdIndex + 40);
        int endReferenceIdIndex = startReferenceIdIndex + 40;
        headReference = referenceId;

        int endReferenceNameIndex = responseBody.indexOf("\0");
        String referenceName = responseBody.substring(endReferenceIdIndex + 1, endReferenceNameIndex);

        int startCapListIndex = endReferenceNameIndex + 1;
        int endCapListIndex = responseBody.indexOf("\n", startCapListIndex);
        String capabilityLine = responseBody.substring(startCapListIndex, endCapListIndex);

        postPackFileNegotiation(referenceId, capabilityLine);

    }


    private void postPackFileNegotiation(String referenceId, String capabilities) throws IOException, InterruptedException {

        String payload = "want " + referenceId + " " + capabilities + "\n";

        int length = payload.getBytes(StandardCharsets.UTF_8).length + 4;
        String pktLine = String.format("%04x", length) + payload;

        String doneLine = "0009done\n";
        String completeRequest = pktLine + "0000" + doneLine;


        byte[] completeRequestBytes = completeRequest.getBytes(StandardCharsets.UTF_8);

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(completeRequestBytes);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .version(HttpClient.Version.HTTP_1_1)
                .setHeader("Content-Type", "application/x-git-upload-pack-request")
                .uri(URI.create(gitUrl + "/git-upload-pack"))
                .build();




        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        System.out.println("Status code: " + response.statusCode());


        BufferedInputStream buffered = new BufferedInputStream(response.body());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();


        while (true) {
            // Read the 4-byte pkt-line length
            byte[] lenBytes = buffered.readNBytes(4);
            if (lenBytes.length < 4) break; // End of stream

            int pktLen = Integer.parseInt(new String(lenBytes, StandardCharsets.UTF_8), 16);
            if (pktLen == 0) break; // Flush packet

            int dataLen = pktLen - 5;
            if (dataLen < 1) continue;

            int band = buffered.read(); // side-band marker
            byte[] data = buffered.readNBytes(dataLen); // actual data

            switch (band) {
                case 1: // pack data
                    byteArrayOutputStream.write(data);
                    //System.out.write(data); // write to stdout or save to file
                    break;
                case 2: // progress messages
                    System.err.write(data);
                    break;
                case 3: // fatal error
                    System.err.write(data);
                    throw new IOException("Fatal error from server: " + new String(data));
            }
        }

        try {
            HashString.hashAndSavePack(byteArrayOutputStream.toByteArray(), dirName);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        parsePackFile(byteArrayOutputStream);


    }

    private void parsePackFile(ByteArrayOutputStream packStream) throws IOException {
        CountingPushbackInputStream buffered = new CountingPushbackInputStream(new ByteArrayInputStream(packStream.toByteArray()), 512);


        // Read pack header
        byte[] headPack = buffered.readNBytes(4);
        byte[] headVersion = buffered.readNBytes(4);
        byte[] headObjectsCount = buffered.readNBytes(4);

        String headPackString = new String(headPack, StandardCharsets.US_ASCII);
        int headVersionInt = ByteBuffer.wrap(headVersion).order(ByteOrder.BIG_ENDIAN).getInt();
        int headObjectsCountInt = ByteBuffer.wrap(headObjectsCount).order(ByteOrder.BIG_ENDIAN).getInt();

        if (!"PACK".equals(headPackString)) {
            throw new IOException("Invalid pack file header: expected 'PACK', found '" + headPackString + "'");
        }

        if (headVersionInt != 2 && headVersionInt != 3) {
            throw new IOException("Unsupported pack version: " + headVersionInt);
        }

        System.out.println("Head Pack: " + headPackString);
        System.out.println("Version: " + headVersionInt);
        System.out.println("Objects Count: " + headObjectsCountInt);

        for (int i = 0; i < headObjectsCountInt; i++) {
            long currentObjectOffset = buffered.getBytesRead();
            int headerByte = buffered.read();
            if (headerByte == -1) throw new IOException("Unexpected end of stream");

            int objectType = (headerByte >> 4) & 0x07;
            long size = headerByte & 0x0F;
            int shift = 4;

            // Parse size continuation bytes
            while ((headerByte & 0x80) != 0) {
                headerByte = buffered.read();
                if (headerByte == -1) throw new IOException("Unexpected end of stream");
                size |= (long)(headerByte & 0x7F) << shift;
                shift += 7;
            }

            switch (objectType) {
                case 1, 2, 3, 4 -> {
                    // Commit, Tree, Blob, Tag objects
                    byte[] decompressedData = ZLibCompression.decompressNextZlibBlock(buffered);
                    String sha = getSha(decompressedData, objectType);
                    ObjectEntry entry = new ObjectEntry(objectType, size, decompressedData);
                    objectEntries.put(sha, entry);
                    offsetToEntry.put(currentObjectOffset, entry);
                }
                case 6 -> {
                    // OBJ_OFS_DELTA
                    long baseOffsetRelative  = readOffset(buffered);
                    long baseOffsetAbsolute = currentObjectOffset - baseOffsetRelative;
                    byte[] decompressedDelta = ZLibCompression.decompressNextZlibBlock(buffered);

                    ObjectEntry deltaEntry = new ObjectEntry(objectType, size, decompressedDelta, baseOffsetAbsolute);
                    pendingDeltas.add(deltaEntry);
                    offsetToEntry.put(currentObjectOffset, deltaEntry);
                }
                case 7 -> {
                    // OBJ_REF_DELTA
                    byte[] baseSha1Bytes = buffered.readNBytes(20);
                    if (baseSha1Bytes.length != 20) throw new IOException("Failed to read full SHA1 reference");
                    String baseSha1 = HashString.hashByteToStringHex(baseSha1Bytes);

                    byte[] decompressedDelta = ZLibCompression.decompressNextZlibBlock(buffered);
                    ObjectEntry deltaEntry = new ObjectEntry(objectType, size, decompressedDelta, baseSha1);
                    pendingDeltas.add(deltaEntry);
                    offsetToEntry.put(currentObjectOffset, deltaEntry);
                }
                default -> throw new IOException("Unknown object type: " + objectType);
            }
        }

        if (!pendingDeltas.isEmpty()){
            applyDeltas();
        }

        checkOut();
        writeObjects();
    }

    private void writeObjects(){
        for (Map.Entry<String, ObjectEntry> entry : objectEntries.entrySet()) {
            String sha = entry.getKey();
            ObjectEntry obj = entry.getValue();

            obj.getData();

            writeObjectsToDisk(sha, obj.getData());

        }
    }

    private void writeObjectsToDisk(String sha, byte[] data){
        Path path = Paths.get(dirName,".git", "objects", sha.substring(0, 2), sha.substring(2));
        try {
            Files.createDirectories(path.getParent());

            ZLibCompression.compress(path, data);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkOut() throws IOException {
        ObjectEntry headCommit = objectEntries.get(headReference);
        if (headCommit == null) {
            throw new RuntimeException("Head commit not found");
        }

        String rootTreeSha = getTreeSha(headCommit.getData());
        ObjectEntry rootTree = objectEntries.get(rootTreeSha);
        if (rootTree == null) {
            throw new RuntimeException("Root tree not found");
        }


        Path rootPath = Paths.get(dirName);
        Files.createDirectories(rootPath);

        readTreeObject(rootTree.getData(), rootPath);

    }

    private void readTreeObject(byte[] rootTreeData, Path currentDir) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(rootTreeData);
        List<TreeEntry> entries = new ArrayList<>();

        while (stream.available() > 0){
            StringBuilder modeBuilder = new StringBuilder();
            int b;
            while ((b = stream.read()) != 0x20){
                modeBuilder.append((char)b);
            }
            String mode = modeBuilder.toString();

            StringBuilder nameBuilder = new StringBuilder();
            while ((b = stream.read()) != 0x0){
                nameBuilder.append((char) b);
            }
            String name = nameBuilder.toString();


            byte[] shaBytes = new byte[20];
            stream.read(shaBytes);

            entries.add(new TreeEntry(mode, name, shaBytes));

        }

        for (TreeEntry entry : entries) {
            String entrySha = entry.toHexString(entry.getSha()).toLowerCase();
            ObjectEntry entryObject = objectEntries.get(entrySha);
            if (entryObject == null) {
                continue;
            }

            Path entryPath = currentDir.resolve(entry.getName());

            if (entry.getMode().equals("40000")){

                Files.createDirectories(entryPath);
                readTreeObject(entryObject.getData(), entryPath);
            } else{
                Files.write(entryPath, entryObject.getData());
            }
        }

    }

    private String getTreeSha(byte[] treeData) throws IOException {
        String commitText = new String(treeData, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(new StringReader(commitText));

        String line;
        String treeSha = null;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("tree ")) {
                treeSha = line.substring(5);
                break;
            }
        }

        if (treeSha == null) {
            throw new IOException("Failed to find tree SHA in commit");
        }

        return treeSha;

    }


    private void applyDeltas() throws IOException {
        List<ObjectEntry> resolved = new ArrayList<>();

        for (ObjectEntry delta : pendingDeltas) {
            ObjectEntry baseEntry = resolveBaseEntry(delta);

            byte[] baseData = baseEntry.getData();
            int baseType = baseEntry.getType();

            byte[] deltaData = delta.getData();
            byte[] reconstructedData = applyDelta(baseData, deltaData);

            if (reconstructedData.length == 0) {
                throw new IOException("Reconstructed data is empty");
            }

            String sha1 = getSha(reconstructedData, baseType);
            ObjectEntry resolvedEntry = new ObjectEntry(baseType, reconstructedData.length, reconstructedData);

            objectEntries.put(sha1, resolvedEntry);

            // Update offsetToEntry to point to resolved entry instead of delta
            Long deltaOffset = null;
            for (Map.Entry<Long, ObjectEntry> entry : offsetToEntry.entrySet()) {
                if (entry.getValue() == delta) {
                    deltaOffset = entry.getKey();
                    break;
                }
            }
            if (deltaOffset != null) {
                offsetToEntry.put(deltaOffset, resolvedEntry);
            }

            resolved.add(delta);
        }

        pendingDeltas.removeAll(resolved);
    }


    private byte[] applyDelta(byte[] base, byte[] delta) throws IOException {
        int deltaPos = 0;

        deltaPos += varIntLength(delta, deltaPos);

        long resultSize = decodeVarInt(delta, deltaPos);
        deltaPos += varIntLength(delta, deltaPos);

        ByteArrayOutputStream result = new ByteArrayOutputStream((int) resultSize);

        while (deltaPos < delta.length) {
            int opcode = delta[deltaPos++] & 0xFF;
            if ((opcode & 0x80) != 0) {
                // Copy command
                int copyOffset = 0;
                int copySize = 0;

                if ((opcode & 0x01) != 0) copyOffset = delta[deltaPos++] & 0xFF;
                if ((opcode & 0x02) != 0) copyOffset |= (delta[deltaPos++] & 0xFF) << 8;
                if ((opcode & 0x04) != 0) copyOffset |= (delta[deltaPos++] & 0xFF) << 16;
                if ((opcode & 0x08) != 0) copyOffset |= (delta[deltaPos++] & 0xFF) << 24;

                if ((opcode & 0x10) != 0) copySize = delta[deltaPos++] & 0xFF;
                if ((opcode & 0x20) != 0) copySize |= (delta[deltaPos++] & 0xFF) << 8;
                if ((opcode & 0x40) != 0) copySize |= (delta[deltaPos++] & 0xFF) << 16;

                if (copySize == 0) copySize = 0x10000;

                if (copyOffset + copySize > base.length) {
                    throw new IOException("Copy command exceeds base object size");
                }

                result.write(base, copyOffset, copySize);
            } else if (opcode != 0) {
                // Insert command
                if (deltaPos + opcode > delta.length) {
                    throw new IOException("Insert command exceeds delta length");
                }
                result.write(delta, deltaPos, opcode);
                deltaPos += opcode;
            } else {
                throw new IOException("Invalid delta opcode 0");
            }
        }

        if (result.size() != resultSize) {
            throw new IOException("Delta application failed: result size mismatch");
        }

        return result.toByteArray();
    }

    private ObjectEntry resolveBaseEntry(ObjectEntry delta) throws IOException {
        ObjectEntry baseEntry;

        if (delta.getBaseSha1() != null) {
            baseEntry = objectEntries.get(delta.getBaseSha1());
            if (baseEntry == null) {
                throw new IOException("Base object not found: " + delta.getBaseSha1());
            }
        } else {
            baseEntry = offsetToEntry.get(delta.getBaseOffset());
            if (baseEntry == null) {
                throw new IOException("Base object not found at offset: " + delta.getBaseOffset());
            }
        }

        if (isDelta(baseEntry.getType())) {
            baseEntry = applyDeltaForEntry(baseEntry);
        }

        return baseEntry;
    }



    private ObjectEntry applyDeltaForEntry(ObjectEntry deltaEntry) throws IOException {
        ObjectEntry baseEntry = resolveBaseEntry(deltaEntry);

        byte[] baseData = baseEntry.getData();
        byte[] deltaData = deltaEntry.getData();
        byte[] reconstructed = applyDelta(baseData, deltaData);

        int actualType = baseEntry.getType();
        System.out.println("Actual Type: " + actualType);
        deltaEntry.setActualType(actualType);
        String sha = getSha(reconstructed, actualType);
        ObjectEntry resolvedEntry = new ObjectEntry(actualType, reconstructed.length, reconstructed);


        objectEntries.put(sha, resolvedEntry);

        if (deltaEntry.getBaseOffset() != null) {
            offsetToEntry.put(deltaEntry.getBaseOffset(), resolvedEntry);
        }

        pendingDeltas.remove(deltaEntry);

        return resolvedEntry;
    }

    private boolean isDelta(int type) {
        return type == 6 || type == 7;
    }


    private long readOffset(CountingPushbackInputStream input) throws IOException {
        int c = input.read();
        if (c == -1) throw new IOException("Unexpected end of stream when reading offset");
        long offset = c & 0x7F;
        while ((c & 0x80) != 0) {
            c = input.read();
            if (c == -1) throw new IOException("Unexpected end of stream when reading offset");
            offset = ((offset + 1) << 7) | (c & 0x7F);
        }
        return offset;
    }

    private long decodeVarInt(byte[] buf, int pos) throws IOException {
        long result = 0;
        int shift = 0;
        int i = pos;
        while (true) {
            if (i >= buf.length) throw new IOException("Invalid varint in delta");
            int b = buf[i++] & 0xFF;
            result |= (long)(b & 0x7F) << shift;
            shift += 7;
            if ((b & 0x80) == 0) break;
        }
        return result;
    }

    private int varIntLength(byte[] buf, int pos) {
        int length = 0;
        while (true) {
            int b = buf[pos + length] & 0xFF;
            length++;
            if ((b & 0x80) == 0) break;
        }
        return length;
    }


    private String getSha(byte[] content, int type) throws IOException {
        String typeName = gitTypeNames.get(type);
        String header = typeName + " " + content.length + "\0";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(header.getBytes(StandardCharsets.UTF_8));
        baos.write(content);

        return HashString.hashByteToStringHex(baos.toByteArray());
    }


    private final Map<Integer, String> gitTypeNames = Map.of(
            1, "commit",
            2, "tree",
            3, "blob",
            4, "tag",
            6, "ofs-delta",
            7, "ref-delta"
    );



    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public void setDirName(String dirName){
        this.dirName = dirName;
    }



}
