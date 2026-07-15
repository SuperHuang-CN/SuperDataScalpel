package cn.superhuang.data.scalpel.business.filedataset.storage;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Internal storage for platform-managed file-dataset contents. */
public interface FileObjectStorage {

    StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType);

    FileObjectContent open(String objectKey);

    void delete(String objectKey);

    record StoredFileObject(String eTag) {
    }

    final class FileObjectContent implements AutoCloseable {

        private final InputStream inputStream;
        private final long contentLength;
        private final String contentType;
        private final Runnable abortAction;

        public FileObjectContent(InputStream inputStream, long contentLength, String contentType) {
            this(inputStream, contentLength, contentType, () -> closeInputStream(inputStream));
        }

        public FileObjectContent(InputStream inputStream, long contentLength, String contentType, Runnable abortAction) {
            this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
            this.contentLength = contentLength;
            this.contentType = contentType;
            this.abortAction = Objects.requireNonNull(abortAction, "abortAction");
        }

        public InputStream inputStream() {
            return inputStream;
        }

        public long contentLength() {
            return contentLength;
        }

        public String contentType() {
            return contentType;
        }

        /** Stops an incomplete remote response without first draining its remaining bytes. */
        public void abort() {
            abortAction.run();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }

        private static void closeInputStream(InputStream inputStream) {
            try {
                inputStream.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
