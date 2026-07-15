package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounds bytes consumed from decoded streaming content during a sample parse. */
public final class SampledContentSizeLimitInputStream extends FilterInputStream {

    private final long limit;
    private long consumed;

    public SampledContentSizeLimitInputStream(InputStream inputStream, long limit) {
        super(inputStream);
        if (limit < 1) {
            throw new IllegalArgumentException("抽样解析解压后字节上限必须大于零");
        }
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        if (consumed >= limit) {
            return verifyEndOfStream();
        }
        int value = in.read();
        if (value >= 0) {
            consumed++;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (consumed >= limit) {
            return verifyEndOfStream();
        }
        int permitted = (int) Math.min(length, limit - consumed);
        int read = in.read(buffer, offset, permitted);
        if (read > 0) {
            consumed += read;
        }
        return read;
    }

    @Override
    public void close() {
        // The storage service owns the underlying stream lifecycle.
    }

    private int verifyEndOfStream() throws IOException {
        if (in.read() < 0) {
            return -1;
        }
        throw new FileDatasetParsingException("解压后文件内容超过抽样解析大小上限");
    }
}
