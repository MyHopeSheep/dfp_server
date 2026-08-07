package com.dataframe.prase.domain.protocol;

import com.dataframe.prase.domain.model.DecodedFrame;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DecodedFrameDeduplicator {

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");

    public List<DecodedFrame> deduplicateEstimated(
            List<DecodedFrame> candidates,
            BigDecimal dedupToleranceRatio) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(dedupToleranceRatio, "dedupToleranceRatio");
        if (dedupToleranceRatio.signum() <= 0) {
            throw new IllegalArgumentException("dedupToleranceRatio 必须大于 0");
        }
        List<DecodedFrame> orderedCandidates = candidates.stream()
                .sorted(Comparator.comparing(this::primarySortTime)
                        .thenComparing(DecodedFrame::phaseUs))
                .toList();
        List<DecodedFrame> deduplicated = new ArrayList<>();

        for (DecodedFrame candidate : orderedCandidates) {
            int duplicateIndex = findEstimatedDuplicate(deduplicated, candidate, dedupToleranceRatio);
            if (duplicateIndex < 0) {
                deduplicated.add(candidate);
            } else if (isPreferredEstimated(candidate, deduplicated.get(duplicateIndex))) {
                deduplicated.set(duplicateIndex, candidate);
            }
        }
        deduplicated.sort(Comparator.comparing(this::primarySortTime)
                .thenComparing(DecodedFrame::startSeconds));
        List<DecodedFrame> numbered = new ArrayList<>(deduplicated.size());
        for (int index = 0; index < deduplicated.size(); index++) {
            numbered.add(deduplicated.get(index).withFrameNumber(index + 1));
        }
        return List.copyOf(numbered);
    }

    public List<DecodedFrame> deduplicate(List<DecodedFrame> candidates, BigDecimal toleranceUs) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(toleranceUs, "toleranceUs");
        if (toleranceUs.signum() <= 0) {
            throw new IllegalArgumentException("toleranceUs 必须大于 0");
        }
        BigDecimal toleranceSeconds = toleranceUs.divide(MICROSECONDS_PER_SECOND);
        List<DecodedFrame> orderedCandidates = candidates.stream()
                .sorted(Comparator.comparing(DecodedFrame::startSeconds)
                        .thenComparing(DecodedFrame::phaseUs))
                .toList();
        List<DecodedFrame> deduplicated = new ArrayList<>();

        for (DecodedFrame candidate : orderedCandidates) {
            int duplicateIndex = findDuplicate(deduplicated, candidate, toleranceSeconds);
            if (duplicateIndex < 0) {
                deduplicated.add(candidate);
            } else if (isPreferred(candidate, deduplicated.get(duplicateIndex))) {
                deduplicated.set(duplicateIndex, candidate);
            }
        }

        deduplicated.sort(Comparator.comparing(DecodedFrame::startSeconds)
                .thenComparingInt(DecodedFrame::segmentNumber));
        List<DecodedFrame> numbered = new ArrayList<>(deduplicated.size());
        for (int index = 0; index < deduplicated.size(); index++) {
            numbered.add(deduplicated.get(index).withFrameNumber(index + 1));
        }
        return List.copyOf(numbered);
    }

    private int findDuplicate(
            List<DecodedFrame> existing,
            DecodedFrame candidate,
            BigDecimal toleranceSeconds) {
        for (int index = 0; index < existing.size(); index++) {
            DecodedFrame current = existing.get(index);
            if (current.segmentNumber() == candidate.segmentNumber()
                    && (withinTolerance(current, candidate, toleranceSeconds)
                    || representsSameBitPosition(current, candidate))) {
                return index;
            }
        }
        return -1;
    }

    private boolean withinTolerance(
            DecodedFrame current,
            DecodedFrame candidate,
            BigDecimal toleranceSeconds) {
        return current.startSeconds().subtract(candidate.startSeconds()).abs()
                .compareTo(toleranceSeconds) <= 0;
    }

    private boolean representsSameBitPosition(DecodedFrame current, DecodedFrame candidate) {
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

    private boolean isPreferred(DecodedFrame candidate, DecodedFrame current) {
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

    private int findEstimatedDuplicate(
            List<DecodedFrame> existing,
            DecodedFrame candidate,
            BigDecimal dedupToleranceRatio) {
        for (int index = 0; index < existing.size(); index++) {
            DecodedFrame current = existing.get(index);
            BigDecimal toleranceSeconds = current.bitPeriodUs().max(candidate.bitPeriodUs())
                    .multiply(dedupToleranceRatio)
                    .divide(MICROSECONDS_PER_SECOND);
            if (current.startSeconds().subtract(candidate.startSeconds()).abs()
                    .compareTo(toleranceSeconds) <= 0) {
                return index;
            }
        }
        return -1;
    }

    private boolean isPreferredEstimated(DecodedFrame candidate, DecodedFrame current) {
        int rankComparison = Integer.compare(candidateRank(candidate), candidateRank(current));
        if (rankComparison != 0) {
            return rankComparison > 0;
        }
        int pulseComparison = Integer.compare(
                candidate.audit().initialValidSingleBitPulseCount(),
                current.audit().initialValidSingleBitPulseCount());
        if (pulseComparison != 0) {
            return pulseComparison > 0;
        }
        int residualComparison = compareNullableAscending(
                candidate.audit().refinedResidualRatio(), current.audit().refinedResidualRatio());
        if (residualComparison != 0) {
            return residualComparison < 0;
        }
        int safetyComparison = compareNullableDescending(
                candidate.audit().minimumSafetyDistanceRatio(),
                current.audit().minimumSafetyDistanceRatio());
        if (safetyComparison != 0) {
            return safetyComparison < 0;
        }
        int periodComparison = candidate.bitPeriodUs().compareTo(current.bitPeriodUs());
        if (periodComparison != 0) {
            return periodComparison < 0;
        }
        return candidate.phaseUs().compareTo(current.phaseUs()) < 0;
    }

    private int candidateRank(DecodedFrame frame) {
        if (frame.valid()) {
            return frame.audit().tailExtended() ? 3 : 4;
        }
        if (frame.complete()) {
            return 2;
        }
        return 1;
    }

    private int compareNullableAscending(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private int compareNullableDescending(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private BigDecimal primarySortTime(DecodedFrame frame) {
        return frame.audit().preludeStartSeconds() == null
                ? frame.startSeconds()
                : frame.audit().preludeStartSeconds();
    }
}
