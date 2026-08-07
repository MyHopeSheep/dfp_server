package com.dataframe.prase.domain.vo;

import java.math.BigDecimal;

public record RemoteProfileView(
        String key,
        String name,
        String remoteId,
        String charset,
        BigDecimal idleThresholdMs,
        int idleLevel,
        String levelMapping,
        String protocolRuleKey,
        String protocolRuleName) {
}
