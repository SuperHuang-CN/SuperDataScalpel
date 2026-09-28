package cn.superhuang.datascalpel.taskengine.language;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** LSP stdio framing, independently bounded before allocating the payload. */
final class LspFrames {
    static final int MAX_BYTES = 2 * 1024 * 1024;

    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        byte[] separator = {'\r', '\n', '\r', '\n'};
        while (matched < separator.length) {
            int next = input.read();
            if (next < 0) {
                if (header.size() == 0) return null;
                throw new EOFException("Incomplete LSP header");
            }
            if (header.size() >= 8192) throw new IOException("LSP header too large");
            header.write(next);
            matched = next == separator[matched] ? matched + 1 : next == '\r' ? 1 : 0;
        }
        int length = -1;
        for (String line : header.toString(StandardCharsets.US_ASCII).split("\r\n")) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                if (length != -1) throw new IOException("Duplicate content length");
                try { length = Integer.parseInt(line.substring(15).trim()); }
                catch (NumberFormatException ex) { throw new IOException("Invalid LSP length", ex); }
            }
        }
        if (length < 0 || length > MAX_BYTES) throw new IOException("Invalid LSP length");
        byte[] body = input.readNBytes(length);
        if (body.length != length) throw new EOFException("Incomplete LSP payload");
        return body;
    }

    static void write(OutputStream output, byte[] body) throws IOException {
        if (body.length > MAX_BYTES) throw new IOException("LSP payload too large");
        output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }
}
