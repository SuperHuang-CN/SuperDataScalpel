package cn.superhuang.data.scalpel.dialect.geopackage;

/** A malformed or unsupported GeoPackage input. */
public class GeoPackageException extends RuntimeException {

    public GeoPackageException(String message) {
        super(message);
    }

    public GeoPackageException(String message, Throwable cause) {
        super(message, cause);
    }
}
