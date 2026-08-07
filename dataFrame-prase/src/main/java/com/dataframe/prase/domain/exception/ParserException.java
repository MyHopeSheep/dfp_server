package com.dataframe.prase.domain.exception;

import com.dataframe.prase.common.ParserErrorCode;

import java.util.Objects;

public class ParserException extends RuntimeException {

    private final ParserErrorCode errorCode;

    public ParserException(ParserErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public ParserException(ParserErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public ParserException(ParserErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ParserErrorCode errorCode() {
        return errorCode;
    }

    public String code() {
        return errorCode.code();
    }
}
