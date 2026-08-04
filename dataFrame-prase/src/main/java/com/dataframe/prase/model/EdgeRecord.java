package com.dataframe.prase.model;

import java.math.BigDecimal;
import java.util.Objects;

public record EdgeRecord(BigDecimal timeSeconds, int level, long sourceLine) {

    public EdgeRecord {
        Objects.requireNonNull(timeSeconds, "timeSeconds");
        if (level != 0 && level != 1) {
            throw new IllegalArgumentException("电平只能是 0 或 1");
        }
        if (sourceLine < 1) {
            throw new IllegalArgumentException("sourceLine 必须大于 0");
        }
    }
}
