package com.dataframe.prase.signal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record PeriodFit(
        BigDecimal originSeconds,
        BigDecimal periodUs,
        BigDecimal boundaryPhaseUs,
        int normalizedIndexShift,
        BigDecimal maxResidualUs,
        int sampleCount) {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public PeriodFit {
        Objects.requireNonNull(originSeconds, "originSeconds");
        Objects.requireNonNull(periodUs, "periodUs");
        Objects.requireNonNull(boundaryPhaseUs, "boundaryPhaseUs");
        Objects.requireNonNull(maxResidualUs, "maxResidualUs");
        if (periodUs.signum() <= 0) {
            throw new IllegalArgumentException("拟合周期必须大于 0");
        }
        if (boundaryPhaseUs.signum() < 0 || boundaryPhaseUs.compareTo(periodUs) >= 0) {
            throw new IllegalArgumentException("拟合边界相位必须位于 [0, T_est) 内");
        }
        if (maxResidualUs.signum() < 0 || sampleCount < 2) {
            throw new IllegalArgumentException("拟合残差和样本数无效");
        }
    }

    public BigDecimal predictedEdgeTimeUs(int originalBitIndex) {
        return boundaryPhaseUs.add(periodUs.multiply(
                BigDecimal.valueOf((long) originalBitIndex + normalizedIndexShift),
                MATH_CONTEXT), MATH_CONTEXT);
    }

    public BigDecimal residualRatio() {
        return maxResidualUs.divide(periodUs, MATH_CONTEXT);
    }
}
