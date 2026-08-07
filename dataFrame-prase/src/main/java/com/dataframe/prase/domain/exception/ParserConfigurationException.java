package com.dataframe.prase.domain.exception;

import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.exception.ParserException;

public final class ParserConfigurationException extends ParserException {

    
    public ParserConfigurationException(ParserErrorCode errorCode, String message) {
        super(errorCode, message);
    }


    public ParserConfigurationException(ParserErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }


}
