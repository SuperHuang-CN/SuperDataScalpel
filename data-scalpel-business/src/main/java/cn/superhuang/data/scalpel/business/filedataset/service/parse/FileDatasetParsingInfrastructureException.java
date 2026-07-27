package cn.superhuang.data.scalpel.business.filedataset.service.parse;

/** A clearly infrastructure-related parsing failure that may succeed on a later attempt. */
public class FileDatasetParsingInfrastructureException extends RuntimeException {

    public FileDatasetParsingInfrastructureException(String message) {
        super(message);
    }

    public FileDatasetParsingInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
