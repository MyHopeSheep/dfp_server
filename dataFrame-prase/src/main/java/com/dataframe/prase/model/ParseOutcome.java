package com.dataframe.prase.model;

import com.dataframe.prase.signal.LevelMapping;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ParseOutcome(
        Path input,
        Path output,
        int edgeCount,
        int segmentCount,
        List<FrameResult> frames,
        List<BoundaryFragment> boundaryFragments,
        List<Integer> remoteId,
        BigDecimal idleThresholdMs,
        int idleLevel,
        LevelMapping levelMapping,
        LocalDateTime processedAt) {

    public ParseOutcome {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(boundaryFragments, "boundaryFragments");
        Objects.requireNonNull(remoteId, "remoteId");
        Objects.requireNonNull(idleThresholdMs, "idleThresholdMs");
        Objects.requireNonNull(levelMapping, "levelMapping");
        Objects.requireNonNull(processedAt, "processedAt");
        if (edgeCount < 0 || segmentCount < 0) {
            throw new IllegalArgumentException("边沿数和候选区段数不能为负数");
        }
        if (remoteId.size() != 3) {
            throw new IllegalArgumentException("遥控器 ID 必须是 3 个字节");
        }
        if (idleLevel != 0 && idleLevel != 1) {
            throw new IllegalArgumentException("空闲电平只能是 0 或 1");
        }
        frames = List.copyOf(frames);
        boundaryFragments = List.copyOf(boundaryFragments);
        remoteId = List.copyOf(remoteId);
    }

    @Deprecated
    public ParseOutcome(
            Path input,
            Path output,
            int edgeCount,
            int segmentCount,
            List<FrameResult> frames,
            List<BoundaryFragment> boundaryFragments,
            List<Integer> remoteId,
            BigDecimal ignoredBitPeriodUs,
            BigDecimal ignoredPhaseStepUs,
            BigDecimal idleThresholdMs,
            int idleLevel,
            LevelMapping levelMapping,
            LocalDateTime processedAt) {
        this(
                input,
                output,
                edgeCount,
                segmentCount,
                frames,
                boundaryFragments,
                remoteId,
                idleThresholdMs,
                idleLevel,
                levelMapping,
                processedAt);
        Objects.requireNonNull(ignoredBitPeriodUs, "ignoredBitPeriodUs");
        Objects.requireNonNull(ignoredPhaseStepUs, "ignoredPhaseStepUs");
    }

    public long validFrameCount() {
        return frames.stream().filter(FrameResult::valid).count();
    }

    public long invalidFrameCount() {
        return frames.size() - validFrameCount();
    }
}
