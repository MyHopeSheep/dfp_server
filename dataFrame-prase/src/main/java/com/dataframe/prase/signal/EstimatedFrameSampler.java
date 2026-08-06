package com.dataframe.prase.signal;

import com.dataframe.prase.model.EdgeRecord;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EstimatedFrameSampler {

    public static final int PHASE_COUNT = FrameTimingRules.PHASE_COUNT;

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public List<BigDecimal> phaseCandidates(BigDecimal periodUs) {
        requirePositive(periodUs, "periodUs");
        BigDecimal phaseStepUs = periodUs.divide(BigDecimal.valueOf(PHASE_COUNT), MATH_CONTEXT);
        List<BigDecimal> phases = new ArrayList<>(PHASE_COUNT);
        for (int index = 0; index < PHASE_COUNT; index++) {
            phases.add(phaseStepUs.multiply(BigDecimal.valueOf(index), MATH_CONTEXT));
        }
        return List.copyOf(phases);
    }

    public SampledWindow sample(
            List<EdgeRecord> edges,
            BigDecimal logicalStartSeconds,
            BigDecimal periodUs,
            BigDecimal samplePhaseUs,
            int bitCount,
            LevelMapping levelMapping) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(logicalStartSeconds, "logicalStartSeconds");
        Objects.requireNonNull(levelMapping, "levelMapping");
        requirePositive(periodUs, "periodUs");
        Objects.requireNonNull(samplePhaseUs, "samplePhaseUs");
        if (samplePhaseUs.signum() < 0 || samplePhaseUs.compareTo(periodUs) >= 0) {
            throw new IllegalArgumentException("samplePhaseUs 必须位于 [0, periodUs) 内");
        }
        if (bitCount < 0) {
            throw new IllegalArgumentException("bitCount 不能为负数");
        }
        if (edges.isEmpty() || bitCount == 0) {
            return new SampledWindow(List.of(), List.of(), BigDecimal.ZERO);
        }

        BigDecimal periodSeconds = periodUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal phaseSeconds = samplePhaseUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal lastRecordedEdge = edges.get(edges.size() - 1).timeSeconds();
        List<Integer> bits = new ArrayList<>(bitCount);
        List<BigDecimal> times = new ArrayList<>(bitCount);
        BigDecimal minimumSafetyRatio = null;

        for (int bitIndex = 0; bitIndex < bitCount; bitIndex++) {
            BigDecimal sampleTime = logicalStartSeconds.add(phaseSeconds, MATH_CONTEXT)
                    .add(periodSeconds.multiply(BigDecimal.valueOf(bitIndex), MATH_CONTEXT), MATH_CONTEXT);
            if (sampleTime.compareTo(lastRecordedEdge) >= 0) {
                break;
            }
            int previousIndex = previousEdgeIndex(edges, sampleTime);
            if (previousIndex < 0 || previousIndex + 1 >= edges.size()) {
                break;
            }
            EdgeRecord previous = edges.get(previousIndex);
            EdgeRecord next = edges.get(previousIndex + 1);
            bits.add(levelMapping.toBit(previous.level()));
            times.add(sampleTime);

            BigDecimal distance = sampleTime.subtract(previous.timeSeconds()).min(
                    next.timeSeconds().subtract(sampleTime));
            BigDecimal distanceRatio = distance.divide(periodSeconds, MATH_CONTEXT);
            if (minimumSafetyRatio == null || distanceRatio.compareTo(minimumSafetyRatio) < 0) {
                minimumSafetyRatio = distanceRatio;
            }
        }
        return new SampledWindow(
                bits, times, minimumSafetyRatio == null ? BigDecimal.ZERO : minimumSafetyRatio);
    }

    public int logicalLevelAt(
            List<EdgeRecord> edges,
            BigDecimal timeSeconds,
            LevelMapping levelMapping) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(timeSeconds, "timeSeconds");
        Objects.requireNonNull(levelMapping, "levelMapping");
        int index = previousEdgeIndex(edges, timeSeconds);
        if (index < 0) {
            throw new IllegalArgumentException("采样时间早于第一条真实边沿: " + timeSeconds);
        }
        return levelMapping.toBit(edges.get(index).level());
    }

    public int previousEdgeIndex(List<EdgeRecord> edges, BigDecimal timeSeconds) {
        int low = 0;
        int high = edges.size() - 1;
        int candidate = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (edges.get(middle).timeSeconds().compareTo(timeSeconds) <= 0) {
                candidate = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return candidate;
    }

    private void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }

    public record SampledWindow(
            List<Integer> bits,
            List<BigDecimal> sampleTimesSeconds,
            BigDecimal minimumSafetyDistanceRatio) {
        public SampledWindow {
            bits = List.copyOf(bits);
            sampleTimesSeconds = List.copyOf(sampleTimesSeconds);
            Objects.requireNonNull(minimumSafetyDistanceRatio, "minimumSafetyDistanceRatio");
            if (bits.size() != sampleTimesSeconds.size()) {
                throw new IllegalArgumentException("bit 数量与采样时间数量不一致");
            }
        }

        public int directBitCount() {
            return bits.size();
        }
    }
}
