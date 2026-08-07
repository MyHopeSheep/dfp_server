package com.dataframe.prase.domain.vo;

import java.util.Objects;

public record ExcelFileDownload(String fileName, byte[] content) {

    public ExcelFileDownload {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
