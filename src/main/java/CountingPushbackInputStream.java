import java.io.IOException;
import java.io.PushbackInputStream;

public class CountingPushbackInputStream extends PushbackInputStream {
    private long bytesRead = 0;

    public CountingPushbackInputStream(PushbackInputStream in, int size) {
        super(in, size);
    }

    public CountingPushbackInputStream(java.io.InputStream in, int size) {
        super(in, size);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) bytesRead++;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n != -1) bytesRead += n;
        return n;
    }

    @Override
    public void unread(int b) throws IOException {
        super.unread(b);
        if (b != -1) bytesRead--;
    }

    @Override
    public void unread(byte[] b, int off, int len) throws IOException {
        super.unread(b, off, len);
        bytesRead -= len;
    }

    public long getBytesRead() {
        return bytesRead;
    }
}