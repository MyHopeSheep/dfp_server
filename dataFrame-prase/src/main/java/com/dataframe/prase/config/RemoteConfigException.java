package com.dataframe.prase.config;

import com.dataframe.prase.enums.DfpErrorCode;
import com.dataframe.prase.exception.DfpException;

public final class RemoteConfigException extends DfpException {

    
    public RemoteConfigException(DfpErrorCode errorCode, String message) {
        super(errorCode, message);
    }


    public RemoteConfigException(DfpErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }


}
