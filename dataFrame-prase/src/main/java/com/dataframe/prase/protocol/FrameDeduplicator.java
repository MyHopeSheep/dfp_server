package com.dataframe.prase.protocol;

import com.dataframe.prase.model.FrameResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FrameDeduplicator {

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");

    public List<FrameResult> deduplicate(List<FrameResult> candidates, BigDecimal toleranceUs) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(toleranceUs, "toleranceUs");
        if (toleranceUs.signum() <= 0) {
            throw new IllegalArgumentException("toleranceUs 必须大于 0");
        }
        BigDecimal toleranceSeconds = toleranceUs.divide(MICROSECONDS_PER_SECOND);
        List<FrameResult> orderedCandidates = candidates.stream()
                .sorted(Comparator.comparing(FrameResult::startSeconds)
                        .thenComparing(FrameResult::phaseUs))
                .toList();
        List<FrameResult> deduplicated = new ArrayList<>();

        for (FrameResult candidate : orderedCandidates) {
            int duplicateIndex = findDuplicate(deduplicated, candidate, toleranceSeconds);
            if (duplicateIndex < 0) {
                deduplicated.add(candidate);
            } else if (isPreferred(candidate, deduplicated.get(duplicateIndex))) {
                deduplicated.set(duplicateIndex, candidate);
            }
        }

        deduplicated.sort(Comparator.comparing(FrameResult::startSeconds)
                .thenComparingInt(FrameResult::segmentNumber));
        List<FrameResult> numbered = new ArrayList<>(deduplicated.size());
        for (int index = 0; index < deduplicated.size(); index++) {
            numbered.add(deduplicated.get(index).withFrameNumber(index + 1));
        }
        return List.copyOf(numbered);
    }

    private int findDuplicate(
            List<FrameResult> existing,
            FrameResult candidate,
            BigDecimal toleranceSeconds) {
        for (int index = 0; index < existing.size(); index++) {
            FrameResult current = existing.get(index);
            if (current.segmentNumber() == candidate.segmentNumber()
                    && (withinTolerance(current, candidate, toleranceSeconds)
                    || representsSameBitPosition(current, candidate))) {
                return index;
            }
        }
        return -1;
    }

    private boolean withinTolerance(
            FrameResult current,
            FrameResult candidate,
            BigDecimal toleranceSeconds) {
        return current.startSeconds().subtract(candidate.startSeconds()).abs()
                .compareTo(toleranceSeconds) <= 0;
    }

    private boolean representsSameBitPosition(FrameResult current, FrameResult candidate) {
        if (!current.headerLocated() || !candidate.headerLocated()
                || current.coreStartBitOffset() < 0 || candidate.coreStartBitOffset() < 0) {
            return false;
        }
        long bitOffsetDistance = Math.abs(
                (long) current.coreStartBitOffset() - candidate.coreStartBitOffset());
        BigDecimal oneBitPeriodSeconds = current.bitPeriodUs()
                .max(candidate.bitPeriodUs())
                .divide(MICROSECONDS_PER_SECOND);
        return bitOffsetDistance <= 1
                && current.startSeconds().subtract(candidate.startSeconds()).abs()
                .compareTo(oneBitPeriodSeconds) <= 0;
    }

    private boolean isPreferred(FrameResult candidate, FrameResult current) {
        if (candidate.valid() != current.valid()) {
            return candidate.valid();
        }
        if (candidate.complete() != current.complete()) {
            return candidate.complete();
        }
        int startComparison = candidate.startSeconds().compareTo(current.startSeconds());
        if (startComparison != 0) {
            return startComparison < 0;
        }
        return candidate.phaseUs().compareTo(current.phaseUs()) < 0;
    }
}
