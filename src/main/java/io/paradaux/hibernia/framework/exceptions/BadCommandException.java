package io.paradaux.hibernia.framework.exceptions;

public class BadCommandException extends RuntimeException {
    public BadCommandException(String message) {
        super(message);
    }
}
