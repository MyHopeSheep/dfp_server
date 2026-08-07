package com.dataframe.prase.domain.signal;

import com.dataframe.prase.domain.model.SignalActivitySegment;
import com.dataframe.prase.domain.model.SignalLevelInterval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SignalActivitySegmenter {

    public SegmentationResult segment(
            List<SignalLevelInterval> intervals,
            int idleLevel,
            BigDecimal idleThresholdSeconds) {
        Objects.requireNonNull(intervals, "intervals");
        Objects.requireNonNull(idleThresholdSeconds, "idleThresholdSeconds");
        if (idleLevel != 0 && idleLevel != 1) {
            throw new IllegalArgumentException("idleLevel 只能是 0 或 1");
        }
        if (idleThresholdSeconds.signum() <= 0) {
            throw new IllegalArgumentException("idleThresholdSeconds 必须大于 0");
        }

        List<SignalActivitySegment> segments = new ArrayList<>();
        int activityStart = 0;
        boolean leftBoundaryKnown = false;

        for (int index = 0; index < intervals.size(); index++) {
            SignalLevelInterval interval = intervals.get(index);
            if (!isIdleSeparator(interval, idleLevel, idleThresholdSeconds)) {
                continue;
            }
            addSegment(segments, intervals, activityStart, index, leftBoundaryKnown, true);
            activityStart = index + 1;
            leftBoundaryKnown = true;
        }
        addSegment(segments, intervals, activityStart, intervals.size(), leftBoundaryKnown, false);
        return new SegmentationResult(segments);
    }

    private boolean isIdleSeparator(
            SignalLevelInterval interval,
            int idleLevel,
            BigDecimal idleThresholdSeconds) {
        return interval.level() == idleLevel
                && interval.durationSeconds().compareTo(idleThresholdSeconds) >= 0;
    }

    private void addSegment(
            List<SignalActivitySegment> segments,
            List<SignalLevelInterval> intervals,
            int fromIndex,
            int toIndex,
            boolean leftBoundaryKnown,
            boolean rightBoundaryKnown) {
        if (fromIndex >= toIndex) {
            return;
        }
        List<SignalLevelInterval> activityIntervals = List.copyOf(intervals.subList(fromIndex, toIndex));
        BigDecimal start = activityIntervals.get(0).startSeconds();
        BigDecimal end = activityIntervals.get(activityIntervals.size() - 1).endSeconds();
        if (start.compareTo(end) >= 0) {
            return;
        }
        segments.add(new SignalActivitySegment(
                segments.size() + 1,
                start,
                end,
                activityIntervals,
                leftBoundaryKnown,
                rightBoundaryKnown));
    }

    public record SegmentationResult(List<SignalActivitySegment> segments) {
        public SegmentationResult {
            segments = List.copyOf(segments);
        }
    }
}
