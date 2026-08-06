package com.dataframe.prase.enums;

public enum DfpErrorCode {

    INVALID_REQUEST("INVALID_REQUEST", "请求参数不正确"),

    UPLOAD_TOO_LARGE("UPLOAD_TOO_LARGE", "上传文件不能超过 20MB"),

    CONFIG_READ_FAILED("CONFIG_READ_FAILED", "配置文件读取失败"),

    CONFIG_INVALID("CONFIG_INVALID", "配置文件内容不合法"),

    CONFIG_NOT_FOUND("CONFIG_NOT_FOUND", "遥控器配置不存在"),

    CSV_READ_FAILED("CSV_READ_FAILED", "CSV 文件读取失败"),

    PROTOCOL_PARSE_FAILED("PROTOCOL_PARSE_FAILED", "协议解析失败"),

    EXCEL_GENERATION_FAILED("EXCEL_GENERATION_FAILED", "Excel 生成失败"),

    INTERNAL_ERROR("INTERNAL_ERROR", "服务处理失败，请查看运行窗口或日志");

    
    private final String code;
    private final String defaultMessage;

    DfpErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

}
