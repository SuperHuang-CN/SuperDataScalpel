package cn.superhuang.data.scalpel.business.filedataset.service.parse;

/** Recoverable initial-source condition; never substitutes a guessed CRS. */
public final class FileDatasetMissingCrsException extends FileDatasetParsingException {
    private final String wkt;
    public FileDatasetMissingCrsException(String message,String wkt) { super(message); this.wkt=wkt; }
    public String wkt() { return wkt; }
}
