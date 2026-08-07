package com.dataframe.prase.domain.model;

import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import java.util.List;

public record ParserConfigurationSnapshot(
        int schemaVersion,
        List<RemoteProfile> remoteConfigurations,
        List<ProtocolDecodingRules> protocolRuleSets) {

    public ParserConfigurationSnapshot {
        remoteConfigurations = List.copyOf(remoteConfigurations);
        protocolRuleSets = List.copyOf(protocolRuleSets);
    }
}
