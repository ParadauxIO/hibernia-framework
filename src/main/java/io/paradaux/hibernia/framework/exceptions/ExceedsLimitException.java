package io.paradaux.hibernia.framework.exceptions;

public class ExceedsLimitException extends RuntimeException {
    public ExceedsLimitException(String message) {
        super(message);
    }
}
