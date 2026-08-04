package com.dataframe.prase.model;

import java.math.BigDecimal;
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
        String actionDescription) {

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
        if (startSeconds.compareTo(endSeconds) > 0) {
            throw new IllegalArgumentException("帧开始时间不能晚于结束时间");
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
                actionDescription);
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
