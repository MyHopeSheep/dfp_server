package com.dataframe.prase.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParserConfigurationDocument(
        Integer schemaVersion,
        List<RemoteEntry> remoteConfigs,
        List<RuleEntry> protocolRuleSets) {

    public record RemoteEntry(
            String key,
            String name,
            String remoteId,
            String charset,
            BigDecimal idleThresholdMs,
            Integer idleLevel,
            String levelMapping,
            String protocolRuleKey) {
    }

    public record RuleEntry(
            String key,
            String name,
            TimingEntry timing,
            FrameEntry frame,
            List<CommandEntry> commands) {
    }

    public record TimingEntry(
            BigDecimal nominalBitPeriodUs,
            BigDecimal prefilterToleranceRatio,
            Integer minValidPulseCount,
            Integer maxInitialFitPulseCount,
            BigDecimal maxFitResidualRatio,
            BigDecimal tailEdgeToleranceRatio,
            Integer phaseCount,
            BigDecimal dedupToleranceRatio) {
    }

    public record FrameEntry(
            String syncMarkerHex,
            String formalHeaderHex,
            Integer formalFrameBits,
            Integer remoteIdByteOffset,
            Integer commandByteOffset,
            Integer tailByteOffset,
            String tailHex) {
    }

    public record CommandEntry(String code, String channel, String action) {
    }
}
