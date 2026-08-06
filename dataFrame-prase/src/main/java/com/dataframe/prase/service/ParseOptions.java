package com.dataframe.prase.service;

import com.dataframe.prase.signal.LevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ParseOptions(
        Path input,
        Path output,
        List<Integer> remoteId,
        Charset charset,
        BigDecimal idleThresholdMs,
        int idleLevel,
        LevelMapping levelMapping,
        String inputDisplayName) {

    public ParseOptions(
            Path input,
            Path output,
            List<Integer> remoteId,
            Charset charset,
            BigDecimal idleThresholdMs,
            int idleLevel,
            LevelMapping levelMapping) {
        this(input, output, remoteId, charset, idleThresholdMs, idleLevel, levelMapping,
                input.getFileName().toString());
    }

    public ParseOptions {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(remoteId, "remoteId");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(idleThresholdMs, "idleThresholdMs");
        Objects.requireNonNull(levelMapping, "levelMapping");
        Objects.requireNonNull(inputDisplayName, "inputDisplayName");
        if (inputDisplayName.isBlank()) {
            throw new IllegalArgumentException("输入文件名不能为空");
        }
        if (remoteId.size() != 3 || remoteId.stream().anyMatch(value -> value == null || value < 0 || value > 255)) {
            throw new IllegalArgumentException("遥控器 ID 必须是 3 个字节");
        }
        if (idleThresholdMs.signum() <= 0) {
            throw new IllegalArgumentException("空闲阈值必须大于 0");
        }
        if (idleLevel != 0 && idleLevel != 1) {
            throw new IllegalArgumentException("空闲电平只能是 0 或 1");
        }
        remoteId = List.copyOf(remoteId);
        inputDisplayName = inputDisplayName.trim();
    }
}
