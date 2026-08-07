package com.dataframe.prase.controller.advice;

import com.dataframe.prase.domain.exception.ParserConfigurationException;
import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.exception.ParserException;
import com.dataframe.prase.domain.vo.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(ParserConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleRemoteConfig(ParserConfigurationException exception) {
        log.warn("遥控器配置处理失败: code={}", exception.code());
        return ResponseEntity.status(httpStatusOf(exception.errorCode()))
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(ParserException.class)
    public ResponseEntity<ErrorResponse> handleDfpException(ParserException exception) {
        return ResponseEntity.status(httpStatusOf(exception.errorCode()))
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ParserErrorCode.INVALID_REQUEST.code(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadLimit() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(ParserErrorCode.UPLOAD_TOO_LARGE.code(), ParserErrorCode.UPLOAD_TOO_LARGE.defaultMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ParserErrorCode.INVALID_REQUEST.code(), "上传文件和 configKey 都是必填项"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleStaticResourceNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("服务处理发生未预期异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(ParserErrorCode.INTERNAL_ERROR.code(), ParserErrorCode.INTERNAL_ERROR.defaultMessage()));
    }


    private HttpStatus httpStatusOf(ParserErrorCode errorCode) {
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
