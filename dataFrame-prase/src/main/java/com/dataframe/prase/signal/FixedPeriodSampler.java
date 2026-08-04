package com.dataframe.prase.signal;

import com.dataframe.prase.model.ActivitySegment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FixedPeriodSampler {

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");

    private final IntervalBuilder intervalBuilder = new IntervalBuilder();

    public List<SampledBits> sample(
            ActivitySegment segment,
            BigDecimal bitPeriodUs,
            BigDecimal phaseStepUs,
            LevelMapping levelMapping) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(levelMapping, "levelMapping");
        requirePositive(bitPeriodUs, "bitPeriodUs");
        requirePositive(phaseStepUs, "phaseStepUs");

        BigDecimal periodSeconds = bitPeriodUs.divide(MICROSECONDS_PER_SECOND);
        List<SampledBits> results = new ArrayList<>();
        for (BigDecimal phaseUs = BigDecimal.ZERO;
             phaseUs.compareTo(bitPeriodUs) < 0;
             phaseUs = phaseUs.add(phaseStepUs)) {
            results.add(samplePhase(
                    segment, bitPeriodUs, periodSeconds, phaseUs, levelMapping));
        }
        return List.copyOf(results);
    }

    private SampledBits samplePhase(
            ActivitySegment segment,
            BigDecimal bitPeriodUs,
            BigDecimal periodSeconds,
            BigDecimal phaseUs,
            LevelMapping levelMapping) {
        BigDecimal phaseSeconds = phaseUs.divide(MICROSECONDS_PER_SECOND);
        BigDecimal sampleTime = segment.startSeconds().add(phaseSeconds);
        List<Integer> bits = new ArrayList<>();
        List<BigDecimal> sampleTimes = new ArrayList<>();

        while (sampleTime.compareTo(segment.endSeconds()) < 0) {
            int csvLevel = intervalBuilder.levelAt(segment.intervals(), sampleTime);
            bits.add(levelMapping.toBit(csvLevel));
            sampleTimes.add(sampleTime);
            sampleTime = sampleTime.add(periodSeconds);
        }
        return new SampledBits(bitPeriodUs, phaseUs, bits, sampleTimes);
    }

    private void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }

    public record SampledBits(
            BigDecimal bitPeriodUs,
            BigDecimal phaseUs,
            List<Integer> bits,
            List<BigDecimal> sampleTimesSeconds) {

        public SampledBits {
            Objects.requireNonNull(bitPeriodUs, "bitPeriodUs");
            Objects.requireNonNull(phaseUs, "phaseUs");
            bits = List.copyOf(bits);
            sampleTimesSeconds = List.copyOf(sampleTimesSeconds);
            if (bits.size() != sampleTimesSeconds.size()) {
                throw new IllegalArgumentException("bit 数量与采样时间数量不一致");
            }
        }

        public String bitString() {
            StringBuilder builder = new StringBuilder(bits.size());
            for (int bit : bits) {
                builder.append(bit);
            }
            return builder.toString();
        }
    }
}
