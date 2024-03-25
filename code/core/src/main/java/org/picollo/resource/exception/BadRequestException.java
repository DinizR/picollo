/*
* BadRequestException.java
 */
package org.picollo.resource.exception;

/**
 * Exception class used on REST protocol for BAD REQUEST
 * @author rdinis
 * @since 2022-05-01
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(final String message) {
        super(message);
    }

    public BadRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
