package com.dataframe.prase.signal;

import com.dataframe.prase.model.EdgeRecord;
import com.dataframe.prase.model.LevelInterval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IntervalBuilder {

    public List<LevelInterval> build(List<EdgeRecord> edges) {
        Objects.requireNonNull(edges, "edges");
        List<LevelInterval> intervals = new ArrayList<>();
        for (int index = 0; index + 1 < edges.size(); index++) {
            EdgeRecord current = edges.get(index);
            EdgeRecord next = edges.get(index + 1);
            intervals.add(new LevelInterval(
                    current.timeSeconds(),
                    next.timeSeconds(),
                    current.level(),
                    current.sourceLine()));
        }
        return List.copyOf(intervals);
    }

    public int levelAt(List<LevelInterval> intervals, BigDecimal timeSeconds) {
        Objects.requireNonNull(intervals, "intervals");
        Objects.requireNonNull(timeSeconds, "timeSeconds");
        int low = 0;
        int high = intervals.size() - 1;
        int candidate = -1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            LevelInterval interval = intervals.get(middle);
            if (interval.startSeconds().compareTo(timeSeconds) <= 0) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        if (candidate >= 0 && intervals.get(candidate).contains(timeSeconds)) {
            return intervals.get(candidate).level();
        }
        throw new IllegalArgumentException("采样时间不在已知电平区间内: " + timeSeconds);
    }
}
