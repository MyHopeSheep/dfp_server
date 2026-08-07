package com.dataframe.prase.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record SignalActivitySegment(
        int number,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        List<SignalLevelInterval> intervals,
        boolean leftBoundaryKnown,
        boolean rightBoundaryKnown) {

    public SignalActivitySegment {
        if (number < 1) {
            throw new IllegalArgumentException("候选区段编号必须大于 0");
        }
        Objects.requireNonNull(startSeconds, "startSeconds");
        Objects.requireNonNull(endSeconds, "endSeconds");
        Objects.requireNonNull(intervals, "intervals");
        if (startSeconds.compareTo(endSeconds) >= 0) {
            throw new IllegalArgumentException("候选区段开始时间必须早于结束时间");
        }
        if (intervals.isEmpty()) {
            throw new IllegalArgumentException("候选区段必须包含电平区间");
        }
        intervals = List.copyOf(intervals);
    }

    public boolean isFileBoundarySegment() {
        return !leftBoundaryKnown || !rightBoundaryKnown;
    }
}
