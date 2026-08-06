package com.dataframe.prase.signal;

import com.dataframe.prase.model.EdgeRecord;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LocalPeriodEstimator {

    public static final BigDecimal MIN_SINGLE_BIT_PULSE_US = FrameTimingRules.MIN_SINGLE_BIT_PULSE_US;
    public static final BigDecimal MAX_SINGLE_BIT_PULSE_US = FrameTimingRules.MAX_SINGLE_BIT_PULSE_US;
    public static final int MIN_VALID_PULSE_COUNT = FrameTimingRules.MIN_VALID_PULSE_COUNT;
    public static final BigDecimal MAX_FIT_RESIDUAL_RATIO = FrameTimingRules.MAX_FIT_RESIDUAL_RATIO;

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public List<InitialCandidate> findInitialCandidates(List<EdgeRecord> edges) {
        Objects.requireNonNull(edges, "edges");
        if (edges.size() < MIN_VALID_PULSE_COUNT + 1) {
            return List.of();
        }

        List<InitialCandidate> candidates = new ArrayList<>();
        for (int start = 0; start + MIN_VALID_PULSE_COUNT < edges.size(); start++) {
            int pulseCount = 0;
            while (pulseCount < FrameTimingRules.MAX_INITIAL_FIT_PULSE_COUNT
                    && start + pulseCount + 1 < edges.size()
                    && isSingleBitPulse(edges.get(start + pulseCount), edges.get(start + pulseCount + 1))) {
                pulseCount++;
            }
            if (pulseCount < MIN_VALID_PULSE_COUNT) {
                continue;
            }

            int end = start + pulseCount;
            List<EdgeRecord> window = List.copyOf(edges.subList(start, end + 1));
            Optional<PeriodFit> fit = fit(window, consecutiveIndexes(window.size()));
            if (fit.isPresent() && isReliable(fit.get())) {
                candidates.add(new InitialCandidate(start, end, pulseCount, fit.get()));
            }
        }
        return List.copyOf(candidates);
    }

    public Optional<PeriodFit> fit(List<EdgeRecord> edges, List<Integer> bitIndexes) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(bitIndexes, "bitIndexes");
        if (edges.size() != bitIndexes.size() || edges.size() < 2) {
            return Optional.empty();
        }

        EdgeRecord origin = edges.get(0);
        BigDecimal xSum = BigDecimal.ZERO;
        BigDecimal ySum = BigDecimal.ZERO;
        for (int index = 0; index < edges.size(); index++) {
            EdgeRecord edge = edges.get(index);
            int bitIndex = bitIndexes.get(index);
            if (index > 0 && (edge.timeSeconds().compareTo(edges.get(index - 1).timeSeconds()) <= 0
                    || bitIndex <= bitIndexes.get(index - 1))) {
                return Optional.empty();
            }
            xSum = xSum.add(BigDecimal.valueOf(bitIndex));
            ySum = ySum.add(edgeTimeUs(edge, origin));
        }
        BigDecimal sampleCount = BigDecimal.valueOf(edges.size());
        BigDecimal xBar = xSum.divide(sampleCount, MATH_CONTEXT);
        BigDecimal yBar = ySum.divide(sampleCount, MATH_CONTEXT);

        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (int index = 0; index < edges.size(); index++) {
            BigDecimal x = BigDecimal.valueOf(bitIndexes.get(index)).subtract(xBar);
            BigDecimal y = edgeTimeUs(edges.get(index), origin);
            numerator = numerator.add(x.multiply(y.subtract(yBar), MATH_CONTEXT));
            denominator = denominator.add(x.multiply(x, MATH_CONTEXT));
        }
        if (denominator.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal periodUs = numerator.divide(denominator, MATH_CONTEXT);
        if (periodUs.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal boundaryPhaseRaw = yBar
                .subtract(periodUs.multiply(xBar, MATH_CONTEXT), MATH_CONTEXT);
        BigDecimal shiftDecimal = boundaryPhaseRaw.divide(periodUs, 0, RoundingMode.FLOOR);
        int shift;
        try {
            shift = shiftDecimal.intValueExact();
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
        BigDecimal boundaryPhase = boundaryPhaseRaw.subtract(
                periodUs.multiply(shiftDecimal, MATH_CONTEXT), MATH_CONTEXT);

        BigDecimal maxResidual = BigDecimal.ZERO;
        for (int index = 0; index < edges.size(); index++) {
            BigDecimal predicted = boundaryPhase.add(periodUs.multiply(
                    BigDecimal.valueOf((long) bitIndexes.get(index) + shift), MATH_CONTEXT), MATH_CONTEXT);
            BigDecimal actual = edgeTimeUs(edges.get(index), origin);
            BigDecimal residual = actual.subtract(predicted).abs();
            if (residual.compareTo(maxResidual) > 0) {
                maxResidual = residual;
            }
        }
        return Optional.of(new PeriodFit(
                origin.timeSeconds(), periodUs, boundaryPhase, shift, maxResidual, edges.size()));
    }

    public static boolean isSingleBitPulseUs(BigDecimal pulseUs) {
        Objects.requireNonNull(pulseUs, "pulseUs");
        return pulseUs.compareTo(MIN_SINGLE_BIT_PULSE_US) >= 0
                && pulseUs.compareTo(MAX_SINGLE_BIT_PULSE_US) <= 0;
    }

    public boolean isReliable(PeriodFit fit) {
        Objects.requireNonNull(fit, "fit");
        return fit.residualRatio().compareTo(MAX_FIT_RESIDUAL_RATIO) <= 0;
    }

    private boolean isSingleBitPulse(EdgeRecord first, EdgeRecord second) {
        BigDecimal pulseUs = second.timeSeconds().subtract(first.timeSeconds())
                .multiply(MICROSECONDS_PER_SECOND);
        return isSingleBitPulseUs(pulseUs);
    }

    private List<Integer> consecutiveIndexes(int size) {
        List<Integer> indexes = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            indexes.add(index);
        }
        return indexes;
    }

    private BigDecimal edgeTimeUs(EdgeRecord edge, EdgeRecord origin) {
        return edge.timeSeconds().subtract(origin.timeSeconds())
                .multiply(MICROSECONDS_PER_SECOND);
    }

    public record InitialCandidate(
            int firstEdgeIndex,
            int lastEdgeIndex,
            int validSingleBitPulseCount,
            PeriodFit fit) {
        public InitialCandidate {
            Objects.requireNonNull(fit, "fit");
            if (firstEdgeIndex < 0 || lastEdgeIndex < firstEdgeIndex
                    || validSingleBitPulseCount < MIN_VALID_PULSE_COUNT) {
                throw new IllegalArgumentException("初始周期候选范围无效");
            }
        }
    }
}
