package com.dataframe.prase.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record ParserSettingsView(
        int schemaVersion,
        List<RemoteConfig> remoteConfigs,
        List<ProtocolRule> protocolRuleSets) {

    public ParserSettingsView {
        remoteConfigs = List.copyOf(remoteConfigs);
        protocolRuleSets = List.copyOf(protocolRuleSets);
    }

    public record RemoteConfig(
            String key,
            String name,
            String remoteId,
            String charset,
            BigDecimal idleThresholdMs,
            int idleLevel,
            String levelMapping,
            String protocolRuleKey) {
    }

    public record ProtocolRule(
            String key,
            String name,
            Timing timing,
            Frame frame,
            List<Command> commands,
            Derived derived) {

        public ProtocolRule {
            commands = List.copyOf(commands);
        }
    }

    public record Timing(
            BigDecimal nominalBitPeriodUs,
            BigDecimal prefilterToleranceRatio,
            int minValidPulseCount,
            int maxInitialFitPulseCount,
            BigDecimal maxFitResidualRatio,
            BigDecimal tailEdgeToleranceRatio,
            int phaseCount,
            BigDecimal dedupToleranceRatio) {
    }

    public record Frame(
            String syncMarkerHex,
            String formalHeaderHex,
            int formalFrameBits,
            int remoteIdByteOffset,
            int commandByteOffset,
            int tailByteOffset,
            String tailHex) {
    }

    public record Command(String code, String channel, String action) {
    }

    public record Derived(
            BigDecimal singleBitPulseMinUs,
            BigDecimal singleBitPulseMaxUs,
            BigDecimal previousPulseToleranceRatio,
            int formalFrameBytes,
            int syncPreludeBits,
            int syncPreludeBytes,
            int syncInclusiveBits,
            int syncInclusiveBytes,
            List<Integer> markerTransitionIndexes,
            String phaseStepExpression) {

        public Derived {
            markerTransitionIndexes = List.copyOf(markerTransitionIndexes);
        }
    }
}
