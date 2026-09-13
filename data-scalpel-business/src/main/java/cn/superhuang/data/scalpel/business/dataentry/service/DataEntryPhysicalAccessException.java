package cn.superhuang.data.scalpel.business.dataentry.service;

public final class DataEntryPhysicalAccessException extends RuntimeException {

    private final String code;
    private final boolean resultUnknown;

    public DataEntryPhysicalAccessException(String code, String message, Throwable cause) {
        this(code, message, cause, false);
    }

    public DataEntryPhysicalAccessException(String code, String message, Throwable cause, boolean resultUnknown) {
        super(message, cause);
        this.code = code;
        this.resultUnknown = resultUnknown;
    }

    public String code() {
        return code;
    }

    public boolean resultUnknown() {
        return resultUnknown;
    }
}
