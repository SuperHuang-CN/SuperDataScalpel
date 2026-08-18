package cn.superhuang.data.scalpel.business.dataentry.service;

public final class DataEntryPhysicalAccessException extends RuntimeException {

    private final String code;

    public DataEntryPhysicalAccessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
