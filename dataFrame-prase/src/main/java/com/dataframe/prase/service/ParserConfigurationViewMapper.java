package com.dataframe.prase.service;

import com.dataframe.prase.domain.model.ParserConfigurationSnapshot;
import com.dataframe.prase.domain.model.RemoteProfile;
import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import com.dataframe.prase.domain.vo.ParserSettingsView;
import com.dataframe.prase.domain.vo.RemoteProfileView;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ParserConfigurationViewMapper {

    List<RemoteProfileView> toRemoteProfileViews(ParserConfigurationSnapshot snapshot) {
        Map<String, String> ruleNames = snapshot.protocolRuleSets().stream()
                .collect(Collectors.toUnmodifiableMap(ProtocolDecodingRules::key, ProtocolDecodingRules::name));
        return snapshot.remoteConfigurations().stream()
                .map(remote -> new RemoteProfileView(
                        remote.key(),
                        remote.name(),
                        formatHex(remote.remoteId()),
                        remote.charset().name(),
                        remote.idleThresholdMs(),
                        remote.idleLevel(),
                        remote.levelMapping().cliValue(),
                        remote.protocolRuleKey(),
                        ruleNames.get(remote.protocolRuleKey())))
                .toList();
    }

    ParserSettingsView toSettingsView(ParserConfigurationSnapshot snapshot) {
        List<ParserSettingsView.RemoteConfig> remotes = snapshot.remoteConfigurations().stream()
                .map(remote -> new ParserSettingsView.RemoteConfig(
                        remote.key(), remote.name(), formatHex(remote.remoteId()), remote.charset().name(),
                        remote.idleThresholdMs(), remote.idleLevel(), remote.levelMapping().cliValue(),
                        remote.protocolRuleKey()))
                .toList();
        List<ParserSettingsView.ProtocolRule> rules = snapshot.protocolRuleSets().stream()
                .map(this::toProtocolRuleView)
                .toList();
        return new ParserSettingsView(snapshot.schemaVersion(), remotes, rules);
    }

    private ParserSettingsView.ProtocolRule toProtocolRuleView(ProtocolDecodingRules rule) {
        ProtocolDecodingRules.Timing timing = rule.timing();
        ProtocolDecodingRules.Frame frame = rule.frame();
        ProtocolDecodingRules.Derived derived = rule.derived();
        return new ParserSettingsView.ProtocolRule(
                rule.key(),
                rule.name(),
                new ParserSettingsView.Timing(
                        timing.nominalBitPeriodUs(), timing.prefilterToleranceRatio(),
                        timing.minValidPulseCount(), timing.maxInitialFitPulseCount(),
                        timing.maxFitResidualRatio(), timing.tailEdgeToleranceRatio(),
                        timing.phaseCount(), timing.dedupToleranceRatio()),
                new ParserSettingsView.Frame(
                        frame.syncMarkerHex(), frame.formalHeaderHex(), frame.formalFrameBits(),
                        frame.remoteIdByteOffset(), frame.commandByteOffset(), frame.tailByteOffset(),
                        frame.tailHex()),
                rule.commands().stream()
                        .map(command -> new ParserSettingsView.Command(
                                "%02X".formatted(command.code()), command.channel(), command.action()))
                        .toList(),
                new ParserSettingsView.Derived(
                        derived.singleBitPulseMinUs(), derived.singleBitPulseMaxUs(),
                        derived.previousPulseToleranceRatio(), derived.formalFrameBytes(),
                        derived.syncPreludeBits(), derived.syncPreludeBytes(),
                        derived.syncInclusiveBits(), derived.syncInclusiveBytes(),
                        derived.markerTransitionIndexes(), derived.phaseStepExpression()));
    }

    private String formatHex(List<Integer> bytes) {
        return bytes.stream()
                .map(value -> "%02X".formatted(value))
                .collect(Collectors.joining(" "));
    }
}
