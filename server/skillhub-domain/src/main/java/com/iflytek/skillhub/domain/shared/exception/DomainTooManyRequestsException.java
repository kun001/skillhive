package com.iflytek.skillhub.domain.shared.exception;

/**
 * Domain exception used when the caller should retry because a shared capacity limit is full.
 */
public class DomainTooManyRequestsException extends LocalizedDomainException {

    public DomainTooManyRequestsException(String messageCode, Object... messageArgs) {
        super(messageCode, messageArgs);
    }

    @Override
    public int statusCode() {
        return 429;
    }
}
