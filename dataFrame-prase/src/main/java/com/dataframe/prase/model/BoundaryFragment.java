package com.dataframe.prase.model;

import java.math.BigDecimal;
import java.util.Objects;

public record BoundaryFragment(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String reason) {

    public BoundaryFragment {
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
