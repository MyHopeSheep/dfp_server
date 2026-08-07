package com.dataframe.prase.domain.model;

import com.dataframe.prase.domain.signal.SignalLevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;

public record RemoteProfile(
        String key,
        String name,
        List<Integer> remoteId,
        Charset charset,
        BigDecimal idleThresholdMs,
        int idleLevel,
        SignalLevelMapping levelMapping,
        String protocolRuleKey) {

    public RemoteProfile {
        remoteId = List.copyOf(remoteId);
    }
}
