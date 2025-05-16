import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class NonClosingBufferedInputStream extends FilterInputStream {
    public NonClosingBufferedInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void close() throws IOException {
        // Override close to do nothing so underlying stream stays open
    }
}