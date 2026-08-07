package com.dataframe.prase.domain.signal;

import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import com.dataframe.prase.domain.model.SignalEdge;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BitPeriodEstimator {

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private final ProtocolDecodingRules.Timing timing;
    private final ProtocolDecodingRules.Derived derived;

    public BitPeriodEstimator(ProtocolDecodingRules ruleSet) {
        Objects.requireNonNull(ruleSet, "ruleSet");
        this.timing = ruleSet.timing();
        this.derived = ruleSet.derived();
    }

    public List<InitialCandidate> findInitialCandidates(List<SignalEdge> edges) {
        Objects.requireNonNull(edges, "edges");
        if (edges.size() < timing.minValidPulseCount() + 1) {
            return List.of();
        }

        List<InitialCandidate> candidates = new ArrayList<>();
        for (int start = 0; start + timing.minValidPulseCount() < edges.size(); start++) {
            int pulseCount = 0;
            while (pulseCount < timing.maxInitialFitPulseCount()
                    && start + pulseCount + 1 < edges.size()
                    && isSingleBitPulse(edges.get(start + pulseCount), edges.get(start + pulseCount + 1))) {
                pulseCount++;
            }
            if (pulseCount < timing.minValidPulseCount()) {
                continue;
            }

            int end = start + pulseCount;
            List<SignalEdge> window = List.copyOf(edges.subList(start, end + 1));
            Optional<BitPeriodFit> fit = fit(window, consecutiveIndexes(window.size()));
            if (fit.isPresent() && isReliable(fit.get())) {
                candidates.add(new InitialCandidate(start, end, pulseCount, fit.get()));
            }
        }
        return List.copyOf(candidates);
    }

    public Optional<BitPeriodFit> fit(List<SignalEdge> edges, List<Integer> bitIndexes) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(bitIndexes, "bitIndexes");
        if (edges.size() != bitIndexes.size() || edges.size() < 2) {
            return Optional.empty();
        }

        SignalEdge origin = edges.get(0);
        BigDecimal xSum = BigDecimal.ZERO;
        BigDecimal ySum = BigDecimal.ZERO;
        for (int index = 0; index < edges.size(); index++) {
            SignalEdge edge = edges.get(index);
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
        return Optional.of(new BitPeriodFit(
                origin.timeSeconds(), periodUs, boundaryPhase, shift, maxResidual, edges.size()));
    }

    public boolean isSingleBitPulseUs(BigDecimal pulseUs) {
        Objects.requireNonNull(pulseUs, "pulseUs");
        return pulseUs.compareTo(derived.singleBitPulseMinUs()) >= 0
                && pulseUs.compareTo(derived.singleBitPulseMaxUs()) <= 0;
    }

    public boolean isReliable(BitPeriodFit fit) {
        Objects.requireNonNull(fit, "fit");
        return fit.residualRatio().compareTo(timing.maxFitResidualRatio()) <= 0;
    }

    private boolean isSingleBitPulse(SignalEdge first, SignalEdge second) {
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

    private BigDecimal edgeTimeUs(SignalEdge edge, SignalEdge origin) {
        return edge.timeSeconds().subtract(origin.timeSeconds())
                .multiply(MICROSECONDS_PER_SECOND);
    }

    public record InitialCandidate(
            int firstEdgeIndex,
            int lastEdgeIndex,
            int validSingleBitPulseCount,
            BitPeriodFit fit) {
        public InitialCandidate {
            Objects.requireNonNull(fit, "fit");
            if (firstEdgeIndex < 0 || lastEdgeIndex < firstEdgeIndex
                    || validSingleBitPulseCount < 1) {
                throw new IllegalArgumentException("初始周期候选范围无效");
            }
        }
    }
}
