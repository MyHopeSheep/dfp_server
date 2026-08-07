package com.dataframe.prase.domain.signal;

import com.dataframe.prase.domain.model.SignalEdge;
import com.dataframe.prase.domain.model.SignalLevelInterval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SignalIntervalBuilder {

    public List<SignalLevelInterval> build(List<SignalEdge> edges) {
        Objects.requireNonNull(edges, "edges");
        List<SignalLevelInterval> intervals = new ArrayList<>();
        for (int index = 0; index + 1 < edges.size(); index++) {
            SignalEdge current = edges.get(index);
            SignalEdge next = edges.get(index + 1);
            intervals.add(new SignalLevelInterval(
                    current.timeSeconds(),
                    next.timeSeconds(),
                    current.level(),
                    current.sourceLine()));
        }
        return List.copyOf(intervals);
    }

    public int levelAt(List<SignalLevelInterval> intervals, BigDecimal timeSeconds) {
        Objects.requireNonNull(intervals, "intervals");
        Objects.requireNonNull(timeSeconds, "timeSeconds");
        int low = 0;
        int high = intervals.size() - 1;
        int candidate = -1;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            SignalLevelInterval interval = intervals.get(middle);
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
