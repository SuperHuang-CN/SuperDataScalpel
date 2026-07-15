package cn.superhuang.data.scalpel.business.filedataset.service.parse;

/** A user-correctable problem in a file's content or its parsing configuration. */
public class FileDatasetParsingException extends RuntimeException {

    public FileDatasetParsingException(String message) {
        super(message);
    }

    public FileDatasetParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
