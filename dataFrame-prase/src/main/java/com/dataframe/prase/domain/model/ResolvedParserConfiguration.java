package com.dataframe.prase.domain.model;

import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import java.util.Objects;

public record ResolvedParserConfiguration(
        RemoteProfile remote,
        ProtocolDecodingRules protocolRuleSet) {

    public ResolvedParserConfiguration {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(protocolRuleSet, "protocolRuleSet");
        if (!remote.protocolRuleKey().equals(protocolRuleSet.key())) {
            throw new IllegalArgumentException("遥控器关联规则与已解析规则不一致");
        }
    }
}
