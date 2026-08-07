package com.dataframe.prase.domain.dto;

import com.dataframe.prase.domain.model.ResolvedParserConfiguration;
import com.dataframe.prase.domain.signal.SignalLevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record FrameParseRequest(
        Path input,
        Path output,
        ResolvedParserConfiguration configuration,
        String inputDisplayName) {

    public FrameParseRequest {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(inputDisplayName, "inputDisplayName");
        if (inputDisplayName.isBlank()) {
            throw new IllegalArgumentException("输入文件名不能为空");
        }
        inputDisplayName = inputDisplayName.trim();
    }

    public List<Integer> remoteId() {
        return configuration.remote().remoteId();
    }

    public Charset charset() {
        return configuration.remote().charset();
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
}
