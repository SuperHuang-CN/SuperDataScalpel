package cn.superhuang.data.scalpel.business.filedataset.storage;

/** Storage access failed without exposing storage-provider details through the API. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
