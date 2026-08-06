package com.dataframe.prase.web;

import java.util.Objects;

public record ParseDownload(String fileName, byte[] content) {

    public ParseDownload {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
