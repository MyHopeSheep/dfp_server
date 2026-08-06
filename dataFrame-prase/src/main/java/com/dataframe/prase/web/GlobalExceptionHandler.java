package com.dataframe.prase.web;

import com.dataframe.prase.config.RemoteConfigException;
import com.dataframe.prase.enums.DfpErrorCode;
import com.dataframe.prase.exception.DfpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(RemoteConfigException.class)
    public ResponseEntity<ApiError> handleRemoteConfig(RemoteConfigException exception) {
        log.warn("遥控器配置处理失败: code={}", exception.code());
        return ResponseEntity.status(httpStatusOf(exception.errorCode()))
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(DfpException.class)
    public ResponseEntity<ApiError> handleDfpException(DfpException exception) {
        return ResponseEntity.status(httpStatusOf(exception.errorCode()))
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError(DfpErrorCode.INVALID_REQUEST.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadLimit() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError(DfpErrorCode.UPLOAD_TOO_LARGE.code(), DfpErrorCode.UPLOAD_TOO_LARGE.defaultMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError(DfpErrorCode.INVALID_REQUEST.code(), "上传文件和 configKey 都是必填项"));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("服务处理发生未预期异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(DfpErrorCode.INTERNAL_ERROR.code(), DfpErrorCode.INTERNAL_ERROR.defaultMessage()));
    }


    private HttpStatus httpStatusOf(DfpErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UPLOAD_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case CONFIG_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFIG_READ_FAILED, CONFIG_INVALID, CSV_READ_FAILED, PROTOCOL_PARSE_FAILED ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case EXCEL_GENERATION_FAILED, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
