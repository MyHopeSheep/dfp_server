package com.dataframe.prase.exception;

import com.dataframe.prase.enums.DfpErrorCode;

import java.util.Objects;

public class DfpException extends RuntimeException {

    private final DfpErrorCode errorCode;

    public DfpException(DfpErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public DfpException(DfpErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public DfpException(DfpErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public DfpErrorCode errorCode() {
        return errorCode;
    }

    public String code() {
        return errorCode.code();
    }
}
