package com.lemmingapex.trilateration;

/**
 * Thrown when a trilateration problem cannot be solved, typically because the optimizer did not
 * converge within the configured iteration or evaluation limits.
 */
public class TrilaterationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message a description of the failure
     * @param cause   the underlying exception
     */
    public TrilaterationException(String message, Throwable cause) {
        super(message, cause);
    }
}
