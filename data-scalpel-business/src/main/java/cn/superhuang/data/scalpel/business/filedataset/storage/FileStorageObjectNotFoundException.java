package cn.superhuang.data.scalpel.business.filedataset.storage;

/** A database record exists but its referenced object is no longer available in storage. */
public class FileStorageObjectNotFoundException extends FileStorageException {

    public FileStorageObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
