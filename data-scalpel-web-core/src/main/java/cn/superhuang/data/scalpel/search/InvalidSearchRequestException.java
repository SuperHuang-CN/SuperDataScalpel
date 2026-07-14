package cn.superhuang.data.scalpel.search;

/** Raised when a search DSL, sort or page parameter cannot be applied to an entity. */
public class InvalidSearchRequestException extends IllegalArgumentException {

    public InvalidSearchRequestException(String message) {
        super(message);
    }

    public InvalidSearchRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
