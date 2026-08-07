package com.dataframe.prase.domain.model;

import com.dataframe.prase.domain.model.ResolvedParserConfiguration;
import com.dataframe.prase.domain.signal.SignalLevelMapping;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record FrameParseResult(
        Path input,
        Path output,
        int edgeCount,
        int segmentCount,
        List<DecodedFrame> frames,
        List<UnrecognizedSignalRange> boundaryFragments,
        ResolvedParserConfiguration configuration,
        String inputDisplayName,
        LocalDateTime processedAt) {

    public FrameParseResult {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(frames, "frames");
        Objects.requireNonNull(boundaryFragments, "boundaryFragments");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(inputDisplayName, "inputDisplayName");
        Objects.requireNonNull(processedAt, "processedAt");
        if (inputDisplayName.isBlank()) {
            throw new IllegalArgumentException("输入文件名不能为空");
        }
        if (edgeCount < 0 || segmentCount < 0) {
            throw new IllegalArgumentException("边沿数和候选区段数不能为负数");
        }
        frames = List.copyOf(frames);
        boundaryFragments = List.copyOf(boundaryFragments);
        inputDisplayName = inputDisplayName.trim();
    }

    public List<Integer> remoteId() {
        return configuration.remote().remoteId();
    }

    public BigDecimal idleThresholdMs() {
        return configuration.remote().idleThresholdMs();
    }

    public int idleLevel() {
        return configuration.remote().idleLevel();
    }

    public SignalLevelMapping levelMapping() {
        return configuration.remote().levelMapping();
    }

    public long validFrameCount() {
        return frames.stream().filter(DecodedFrame::valid).count();
    }

    public long invalidFrameCount() {
        return frames.size() - validFrameCount();
    }
}
