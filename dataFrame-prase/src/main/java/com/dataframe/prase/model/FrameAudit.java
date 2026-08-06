package com.dataframe.prase.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

public record FrameAudit(
        BigDecimal preludeStartSeconds,
        BigDecimal lastAssociatedEdgeSeconds,
        BigDecimal formalStartSeconds,
        BigDecimal formalEndSeconds,
        String periodEstimationBasis,
        int initialValidSingleBitPulseCount,
        BigDecimal initialPeriodUs,
        BigDecimal initialMaxResidualUs,
        BigDecimal initialResidualRatio,
        BigDecimal initialResidualThresholdUs,
        BigDecimal refinedMaxResidualUs,
        BigDecimal refinedResidualRatio,
        BigDecimal refinedResidualThresholdUs,
        BigDecimal samplePhaseUs,
        BigDecimal phaseStepUs,
        int directRecoveredBitCount,
        int finalRecoveredBitCount,
        BigDecimal tailEdgeDeviationUs,
        BigDecimal tailEdgeToleranceUs,
        BigDecimal previousPulseErrorUs,
        String tailRecoveryStatus,
        BigDecimal minimumSafetyDistanceRatio,
        boolean tailExtended) {

    private static final BigDecimal MAX_RESIDUAL_RATIO = new BigDecimal("0.15");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public FrameAudit {
        Objects.requireNonNull(periodEstimationBasis, "periodEstimationBasis");
        Objects.requireNonNull(tailRecoveryStatus, "tailRecoveryStatus");
        if (initialValidSingleBitPulseCount < -1) {
            throw new IllegalArgumentException("初始有效单 bit 脉宽数量不能小于 -1");
        }
        if (directRecoveredBitCount < -1 || finalRecoveredBitCount < -1) {
            throw new IllegalArgumentException("恢复 bit 数不能小于 -1");
        }
        if (directRecoveredBitCount >= 0 && finalRecoveredBitCount >= 0
                && finalRecoveredBitCount < directRecoveredBitCount) {
            throw new IllegalArgumentException("最终恢复 bit 数不能小于直接恢复 bit 数");
        }
        if (tailExtended
                && (directRecoveredBitCount != 103 || finalRecoveredBitCount != 104)) {
            throw new IllegalArgumentException("文件尾扩展只能从 103 bit 补齐到 104 bit");
        }
        if (!tailExtended && directRecoveredBitCount >= 0 && finalRecoveredBitCount >= 0
                && directRecoveredBitCount != finalRecoveredBitCount) {
            throw new IllegalArgumentException("恢复 bit 数增加时必须明确标记文件尾扩展");
        }
        if (formalStartSeconds != null && formalEndSeconds != null
                && formalStartSeconds.compareTo(formalEndSeconds) > 0) {
            throw new IllegalArgumentException("正式帧开始时间不能晚于结束时间");
        }
        if (preludeStartSeconds != null && formalStartSeconds != null
                && preludeStartSeconds.compareTo(formalStartSeconds) > 0) {
            throw new IllegalArgumentException("前导开始时间不能晚于正式帧开始时间");
        }
        if (refinedMaxResidualUs != null) {
            Objects.requireNonNull(preludeStartSeconds, "preludeStartSeconds");
            Objects.requireNonNull(lastAssociatedEdgeSeconds, "lastAssociatedEdgeSeconds");
            Objects.requireNonNull(formalStartSeconds, "formalStartSeconds");
            Objects.requireNonNull(formalEndSeconds, "formalEndSeconds");
            Objects.requireNonNull(initialPeriodUs, "initialPeriodUs");
            Objects.requireNonNull(initialMaxResidualUs, "initialMaxResidualUs");
            Objects.requireNonNull(initialResidualRatio, "initialResidualRatio");
            Objects.requireNonNull(initialResidualThresholdUs, "initialResidualThresholdUs");
            Objects.requireNonNull(refinedResidualRatio, "refinedResidualRatio");
            Objects.requireNonNull(refinedResidualThresholdUs, "refinedResidualThresholdUs");
            Objects.requireNonNull(samplePhaseUs, "samplePhaseUs");
            Objects.requireNonNull(phaseStepUs, "phaseStepUs");
            Objects.requireNonNull(minimumSafetyDistanceRatio, "minimumSafetyDistanceRatio");
            if (initialValidSingleBitPulseCount < 5
                    || directRecoveredBitCount < 0
                    || finalRecoveredBitCount < 0) {
                throw new IllegalArgumentException("本地周期估计审计计数无效");
            }
            if (initialPeriodUs.signum() <= 0 || phaseStepUs.signum() <= 0
                    || initialMaxResidualUs.signum() < 0 || initialResidualRatio.signum() < 0
                    || initialResidualThresholdUs.signum() < 0 || refinedMaxResidualUs.signum() < 0
                    || refinedResidualRatio.signum() < 0 || refinedResidualThresholdUs.signum() < 0
                    || samplePhaseUs.signum() < 0 || minimumSafetyDistanceRatio.signum() < 0) {
                throw new IllegalArgumentException("本地周期估计审计数值无效");
            }
            if (lastAssociatedEdgeSeconds.compareTo(preludeStartSeconds) < 0
                    || lastAssociatedEdgeSeconds.compareTo(formalEndSeconds) > 0) {
                throw new IllegalArgumentException("最后关联边沿时间不在候选帧范围内");
            }
            if (initialMaxResidualUs.divide(initialPeriodUs, MATH_CONTEXT)
                    .compareTo(initialResidualRatio) != 0
                    || initialPeriodUs.multiply(MAX_RESIDUAL_RATIO)
                    .compareTo(initialResidualThresholdUs) != 0) {
                throw new IllegalArgumentException("初始拟合残差派生值与周期不一致");
            }
        }
    }

    public boolean estimated() {
        return refinedMaxResidualUs != null;
    }

    public static FrameAudit notEstimated(
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            BigDecimal samplePhaseUs,
            int recoveredBitCount) {
        return new FrameAudit(
                startSeconds,
                endSeconds,
                startSeconds,
                endSeconds,
                "未实现",
                -1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                samplePhaseUs,
                null,
                recoveredBitCount,
                recoveredBitCount,
                null,
                null,
                null,
                "未实现",
                null,
                false);
    }
}
