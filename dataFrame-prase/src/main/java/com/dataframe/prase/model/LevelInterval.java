package com.dataframe.prase.model;

import java.math.BigDecimal;
import java.util.Objects;

public record LevelInterval(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        int level,
        long sourceLine) {

    public LevelInterval {
        Objects.requireNonNull(startSeconds, "startSeconds");
        Objects.requireNonNull(endSeconds, "endSeconds");
        if (startSeconds.compareTo(endSeconds) > 0) {
            throw new IllegalArgumentException("区间开始时间不能晚于结束时间");
        }
        if (level != 0 && level != 1) {
            throw new IllegalArgumentException("电平只能是 0 或 1");
        }
        if (sourceLine < 1) {
            throw new IllegalArgumentException("sourceLine 必须大于 0");
        }
    }

    public BigDecimal durationSeconds() {
        return endSeconds.subtract(startSeconds);
    }

    public boolean contains(BigDecimal timeSeconds) {
        return startSeconds.compareTo(timeSeconds) <= 0
                && timeSeconds.compareTo(endSeconds) < 0;
    }
}
