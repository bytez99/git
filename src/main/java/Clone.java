import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.regex.Pattern;

public class Clone {

    private static String gitUrl;
    private Map<String, ObjectEntry> objectEntries = new HashMap<>();



    public void cloneRepo(String gitUrl) throws IOException, InterruptedException {
        setGitUrl(gitUrl);
        referenceDiscovery(gitUrl);


    }

    public void referenceDiscovery(String gitUrl) throws IOException, InterruptedException {
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

    public boolean validateResponse(HttpResponse<String> response) {

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

    public void parseSmartReply(String responseBody) throws IOException, InterruptedException {

        int startReferenceIdIndex = responseBody.indexOf("\n") + 9;
        String referenceId = responseBody.substring(startReferenceIdIndex, startReferenceIdIndex + 40);
        int endReferenceIdIndex = startReferenceIdIndex + 40;

        int endReferenceNameIndex = responseBody.indexOf("\0");
        String referenceName = responseBody.substring(endReferenceIdIndex + 1, endReferenceNameIndex);

        int startCapListIndex = endReferenceNameIndex + 1;
        int endCapListIndex = responseBody.indexOf("\n", startCapListIndex);
        String capabilityLine = responseBody.substring(startCapListIndex, endCapListIndex);

//        System.out.println(capabilityLine);

//        String[] capList = capabilityLine.split(" ");


//        HashMap<String, String> capabilitiesHash = new HashMap<>();
//
//        for (String line : capList) {
//            if (line.contains("=")){
//                String[] keyValue = line.split("=");
//                capabilitiesHash.put(keyValue[0], keyValue[1]);
//            }else {
//                capabilitiesHash.put(line, "True");
//            }
//
//        }


        postPackFileNegotiation(referenceId, capabilityLine);

    }


    public void postPackFileNegotiation(String referenceId, String capabilities) throws IOException, InterruptedException {

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

        parsePackFile(byteArrayOutputStream);


    }

    public void parsePackFile(ByteArrayOutputStream packStream) throws IOException {
        PushbackInputStream buffered = new PushbackInputStream(new ByteArrayInputStream(packStream.toByteArray()), 512);

        // Read pack header
        byte[] headPack = buffered.readNBytes(4);
        byte[] headVersion = buffered.readNBytes(4);
        byte[] headObjectsCount = buffered.readNBytes(4);

        String headPackString = new String(headPack, StandardCharsets.US_ASCII);
        int headVersionInt = ByteBuffer.wrap(headVersion).order(ByteOrder.BIG_ENDIAN).getInt();
        int headObjectsCountInt = ByteBuffer.wrap(headObjectsCount).order(ByteOrder.BIG_ENDIAN).getInt();

        System.out.println("Head Pack: " + headPackString);
        System.out.println("Version: " + headVersionInt);
        System.out.println("Objects Count: " + headObjectsCountInt);

        for (int i = 0; i < headObjectsCountInt; i++) {
            int headerByte = buffered.read();
            if (headerByte == -1) throw new IOException("Unexpected end of stream");
            System.out.printf("Initial headerByte: 0x%02X\n", headerByte);

            int objectType = (headerByte >> 4) & 0x07;
            long size = headerByte & 0x0F;
            int shift = 4;

            // Parse size continuation bytes
            while ((headerByte & 0x80) != 0) {
                headerByte = buffered.read();
                if (headerByte == -1) throw new IOException("Unexpected end of stream");
                System.out.printf("Next size byte: 0x%02X\n", headerByte);
                size += (long)(headerByte & 0x7F) << shift;
                shift += 7;
                System.out.printf("Updated size: %d\n", size);
            }

            switch (objectType) {
                case 1, 2, 3 -> {
                    // Commit, Tree, Blob objects
                    // Use the main PushbackInputStream directly
                    byte[] decompressedData = ZLibCompression.decompressNextZlibBlock(buffered);
                    String sha = getSha(decompressedData, objectType);
                    objectEntries.put(sha, new ObjectEntry(objectType, size, decompressedData));
                }

                case 4 -> {
                    // Tag objects - decompress similarly if needed
                    byte[] decompressedData = ZLibCompression.decompressNextZlibBlock(buffered);
                    String sha = getSha(decompressedData, objectType);
                    objectEntries.put(sha, new ObjectEntry(objectType, size, decompressedData));
                }

                case 6 -> {
                    // OBJ_OFS_DELTA
                    // Read offset bytes before decompressing delta data
                    long baseOffset = 0;
                    int c;
                    do {
                        c = buffered.read();
                        if (c == -1) throw new IOException("Unexpected EOF reading base offset");
                        baseOffset = (baseOffset << 7) + (c & 0x7F);
                    } while ((c & 0x80) != 0);
                    System.out.printf("Base offset: %d\n", baseOffset);

                    byte[] decompressedDelta = ZLibCompression.decompressNextZlibBlock(buffered);
                    String sha = getSha(decompressedDelta, objectType);
                    objectEntries.put(sha, new ObjectEntry(objectType, decompressedDelta.length, decompressedDelta, Long.toString(baseOffset)));
                }

                case 7 -> {
                    // OBJ_REF_DELTA
                    // Read 20-byte base SHA1 reference
                    byte[] baseSha1Bytes = buffered.readNBytes(20);
                    if (baseSha1Bytes.length != 20) throw new IOException("Failed to read full SHA1 reference");
                    String baseSha1 = HashString.hashByteToStringHex(baseSha1Bytes);
                    System.out.printf("Base SHA1 reference: %s\n", baseSha1);

                    byte[] decompressedDelta = ZLibCompression.decompressNextZlibBlock(buffered);
                    String sha = getSha(decompressedDelta, objectType);
                    objectEntries.put(sha, new ObjectEntry(objectType, decompressedDelta.length, decompressedDelta, baseSha1));
                }

                default -> {
                    System.err.println("Unknown object type: " + objectType);
                    // Consider throwing or skipping
                }
            }


        }
    }


    public String getSha(byte[] data, int objectType) throws IOException {
        Long dataLength = (long) data.length;
        byte[] sizeInBytes = Long.toString(dataLength).getBytes(StandardCharsets.UTF_8);
        String typeName = gitTypeNames.get(objectType);
        byte[] typeBytes = typeName.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(typeBytes);
        baos.write(' ');
        baos.writeBytes(sizeInBytes);
        baos.write(0);
        baos.write(data);

        byte[] toHash = baos.toByteArray();

        return HashString.hashByteToStringHex(toHash);


    }


    private static final Map<Integer, String> gitTypeNames = Map.of(
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


}
