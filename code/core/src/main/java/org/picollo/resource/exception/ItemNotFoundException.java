/*
 * BadRequestException.java
 */
package org.picollo.resource.exception;

/**
 * Exception class used on REST protocol for NOT FOUND
 * @author rdinis
 * @since 2022-05-01
 */
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(final String message) {
        super(message);
    }

    public ItemNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
