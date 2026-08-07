package com.dataframe.prase.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record UnrecognizedSignalRange(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String reason) {

    public UnrecognizedSignalRange {
        Objects.requireNonNull(startSeconds, "startSeconds");
        Objects.requireNonNull(endSeconds, "endSeconds");
        Objects.requireNonNull(reason, "reason");
        if (startSeconds.compareTo(endSeconds) > 0) {
            throw new IllegalArgumentException("残片开始时间不能晚于结束时间");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("残片原因不能为空");
        }
    }
}
