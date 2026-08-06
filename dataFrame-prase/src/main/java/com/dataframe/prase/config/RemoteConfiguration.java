package com.dataframe.prase.config;

import com.dataframe.prase.signal.LevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

public record RemoteConfiguration(
        String key,
        String name,
        List<Integer> remoteId,
        Charset charset,
        BigDecimal idleThresholdMs,
        int idleLevel,
        LevelMapping levelMapping) {

    public RemoteConfiguration {
        remoteId = List.copyOf(remoteId);
    }
}
