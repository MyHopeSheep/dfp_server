package com.dataframe.prase.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FrameResult(
        int frameNumber,
        int segmentNumber,
        boolean headerLocated,
        boolean complete,
        boolean valid,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal bitPeriodUs,
        BigDecimal phaseUs,
        int coreStartBitOffset,
        String recoveredBits,
        List<Integer> recoveredBytes,
        List<String> failureReasons,
        String channelDescription,
        String actionDescription,
        FrameAudit audit) {

    private static final int FORMAL_FRAME_BITS = 104;
    private static final int PHASE_COUNT = 16;
    private static final BigDecimal MAX_RESIDUAL_RATIO = new BigDecimal("0.15");
    private static final BigDecimal TIMING_ARITHMETIC_TOLERANCE_SECONDS =
            new BigDecimal("0.000000000000000001");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    public FrameResult {
        if (frameNumber < 0 || segmentNumber < 1) {
            throw new IllegalArgumentException("帧序号不能为负数且候选区段序号必须大于 0");
        }
        Objects.requireNonNull(startSeconds, "startSeconds");
        Objects.requireNonNull(endSeconds, "endSeconds");
        Objects.requireNonNull(bitPeriodUs, "bitPeriodUs");
        Objects.requireNonNull(phaseUs, "phaseUs");
        Objects.requireNonNull(recoveredBits, "recoveredBits");
        Objects.requireNonNull(recoveredBytes, "recoveredBytes");
        Objects.requireNonNull(failureReasons, "failureReasons");
        Objects.requireNonNull(channelDescription, "channelDescription");
        Objects.requireNonNull(actionDescription, "actionDescription");
        Objects.requireNonNull(audit, "audit");
        if (startSeconds.compareTo(endSeconds) > 0) {
            throw new IllegalArgumentException("帧开始时间不能晚于结束时间");
        }
        if (bitPeriodUs.signum() <= 0) {
            throw new IllegalArgumentException("bit 周期必须大于 0");
        }
        if (phaseUs.signum() < 0 || phaseUs.compareTo(bitPeriodUs) >= 0) {
            throw new IllegalArgumentException("采样相位必须位于 [0, bit 周期) 内");
        }
        if (coreStartBitOffset < -1) {
            throw new IllegalArgumentException("核心帧 bit 偏移不能小于 -1");
        }
        recoveredBytes = List.copyOf(recoveredBytes);
        failureReasons = List.copyOf(failureReasons);
        if (valid && (!complete || !headerLocated || !failureReasons.isEmpty())) {
            throw new IllegalArgumentException("有效帧必须已定位、完整且没有失败原因");
        }
        if (!valid && failureReasons.isEmpty()) {
            throw new IllegalArgumentException("非有效帧必须包含失败原因");
        }
        if (audit.estimated()) {
            validateEstimatedResult(
                    startSeconds,
                    endSeconds,
                    recoveredBits,
                    recoveredBytes,
                    complete,
                    headerLocated,
                    bitPeriodUs,
                    phaseUs,
                    audit);
        }
    }

    private static void validateEstimatedResult(
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String recoveredBits,
            List<Integer> recoveredBytes,
            boolean complete,
            boolean headerLocated,
            BigDecimal bitPeriodUs,
            BigDecimal phaseUs,
            FrameAudit audit) {
        if (startSeconds.compareTo(audit.formalStartSeconds()) != 0
                || endSeconds.compareTo(audit.formalEndSeconds()) != 0) {
            throw new IllegalArgumentException("帧时间与本地估计审计时间不一致");
        }
        if (phaseUs.compareTo(audit.samplePhaseUs()) != 0) {
            throw new IllegalArgumentException("采样相位与本地估计审计相位不一致");
        }
        BigDecimal expectedDurationSeconds = bitPeriodUs
                .multiply(BigDecimal.valueOf(FORMAL_FRAME_BITS))
                .movePointLeft(6);
        if (endSeconds.subtract(startSeconds).subtract(expectedDurationSeconds).abs()
                .compareTo(TIMING_ARITHMETIC_TOLERANCE_SECONDS) > 0) {
            throw new IllegalArgumentException("正式帧时长与 104 个本地估计周期不一致");
        }
        if (bitPeriodUs.divide(BigDecimal.valueOf(PHASE_COUNT), MATH_CONTEXT)
                .compareTo(audit.phaseStepUs()) != 0) {
            throw new IllegalArgumentException("采样相位步长与本地估计周期不一致");
        }
        if (audit.refinedMaxResidualUs().divide(bitPeriodUs, MATH_CONTEXT)
                .compareTo(audit.refinedResidualRatio()) != 0
                || bitPeriodUs.multiply(MAX_RESIDUAL_RATIO)
                .compareTo(audit.refinedResidualThresholdUs()) != 0) {
            throw new IllegalArgumentException("细化拟合残差派生值与周期不一致");
        }
        if (recoveredBits.length() != audit.finalRecoveredBitCount()) {
            throw new IllegalArgumentException("恢复 bit 串长度与审计计数不一致");
        }
        if (recoveredBytes.size() != recoveredBits.length() / Byte.SIZE) {
            throw new IllegalArgumentException("恢复字节数与完整 bit 数不一致");
        }
        if (!bitsMatchBytes(recoveredBits, recoveredBytes)) {
            throw new IllegalArgumentException("恢复 bit 串内容与恢复字节内容不一致");
        }
        if (complete && (audit.finalRecoveredBitCount() != FORMAL_FRAME_BITS
                || recoveredBits.length() != FORMAL_FRAME_BITS
                || recoveredBytes.size() != 13)) {
            throw new IllegalArgumentException("完整正式帧必须严格包含 104 bit 和 13 字节");
        }
        if (headerLocated && recoveredBytes.size() >= 2
                && (recoveredBytes.get(0) != 0x2D || recoveredBytes.get(1) != 0xD4)) {
            throw new IllegalArgumentException("已定位正式帧必须以 2D D4 开始");
        }
    }

    private static boolean bitsMatchBytes(String bits, List<Integer> bytes) {
        for (int bitIndex = 0; bitIndex < bits.length(); bitIndex++) {
            char bit = bits.charAt(bitIndex);
            if (bit != '0' && bit != '1') {
                return false;
            }
            if (bitIndex / Byte.SIZE < bytes.size()) {
                int shift = 7 - bitIndex % Byte.SIZE;
                int expected = (bytes.get(bitIndex / Byte.SIZE) >>> shift) & 1;
                if (bit - '0' != expected) {
                    return false;
                }
            }
        }
        return true;
    }

    public FrameResult(
            int frameNumber,
            int segmentNumber,
            boolean headerLocated,
            boolean complete,
            boolean valid,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            BigDecimal bitPeriodUs,
            BigDecimal phaseUs,
            int coreStartBitOffset,
            String recoveredBits,
            List<Integer> recoveredBytes,
            List<String> failureReasons,
            String channelDescription,
            String actionDescription) {
        this(
                frameNumber,
                segmentNumber,
                headerLocated,
                complete,
                valid,
                startSeconds,
                endSeconds,
                bitPeriodUs,
                phaseUs,
                coreStartBitOffset,
                recoveredBits,
                recoveredBytes,
                failureReasons,
                channelDescription,
                actionDescription,
                FrameAudit.notEstimated(startSeconds, endSeconds, phaseUs, recoveredBits.length()));
    }

    public FrameResult withFrameNumber(int newFrameNumber) {
        return new FrameResult(
                newFrameNumber,
                segmentNumber,
                headerLocated,
                complete,
                valid,
                startSeconds,
                endSeconds,
                bitPeriodUs,
                phaseUs,
                coreStartBitOffset,
                recoveredBits,
                recoveredBytes,
                failureReasons,
                channelDescription,
                actionDescription,
                audit);
    }

    public String recognitionResult() {
        return valid ? "有效帧" : "非有效帧";
    }

    public String failureReasonText() {
        return String.join("；", failureReasons);
    }

    public String recoveredByteString() {
        return recoveredBytes.stream()
                .map(FrameResult::hex)
                .collect(Collectors.joining(" "));
    }

    public static String hex(int value) {
        return "%02X".formatted(value & 0xFF);
    }
}
