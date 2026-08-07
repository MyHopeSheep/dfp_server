package com.dataframe.prase.domain.configuration;

import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.dto.ParserConfigurationDocument;
import com.dataframe.prase.domain.exception.ParserConfigurationException;
import com.dataframe.prase.domain.model.ParserConfigurationSnapshot;
import com.dataframe.prase.domain.model.RemoteProfile;
import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import com.dataframe.prase.domain.signal.SignalLevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ParserConfigurationValidator {

    private static final int SCHEMA_VERSION = 1;
    private static final String SYNC_MARKER = "AA 2D D4";
    private static final String FORMAL_HEADER = "2D D4";
    private static final String TAIL = "AA AA";

    public ParserConfigurationSnapshot validate(ParserConfigurationDocument document) {
        validateTopLevel(document);
        List<ProtocolDecodingRules> rules = validateRules(document.protocolRuleSets());
        Map<String, ProtocolDecodingRules> ruleByKey = rules.stream()
                .collect(Collectors.toUnmodifiableMap(ProtocolDecodingRules::key, rule -> rule));
        List<RemoteProfile> remotes = validateRemotes(document.remoteConfigs(), ruleByKey);
        return new ParserConfigurationSnapshot(document.schemaVersion(), remotes, rules);
    }

    private void validateTopLevel(ParserConfigurationDocument document) {
        if (document == null) {
            throw invalid("配置文档不能为空");
        }
        if (document.schemaVersion() == null || document.schemaVersion() != SCHEMA_VERSION) {
            throw invalid("schemaVersion 必须固定为 1");
        }
        if (document.remoteConfigs() == null || document.remoteConfigs().isEmpty()) {
            throw invalid("remoteConfigs 至少包含一套遥控器配置");
        }
        if (document.protocolRuleSets() == null || document.protocolRuleSets().isEmpty()) {
            throw invalid("protocolRuleSets 至少包含一套协议规则");
        }
    }

    private List<ProtocolDecodingRules> validateRules(List<ParserConfigurationDocument.RuleEntry> entries) {
        Set<String> keys = uniqueKeys(entries.stream()
                .map(entry -> entry == null ? null : entry.key())
                .toList(), "protocolRuleSets");
        List<ProtocolDecodingRules> rules = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ParserConfigurationDocument.RuleEntry raw = entries.get(index);
            if (raw == null) {
                throw invalid("protocolRuleSets[" + index + "] 不能为空");
            }
            String prefix = "protocolRuleSets[" + index + "].";
            String key = requireText(raw.key(), prefix + "key");
            if (!keys.contains(key)) {
                throw invalid(prefix + "key 无效");
            }
            String name = requireText(raw.name(), prefix + "name");
            ProtocolDecodingRules.Timing timing = validateTiming(raw.timing(), prefix + "timing.");
            ProtocolDecodingRules.Frame frame = validateFrame(raw.frame(), prefix + "frame.");
            List<ProtocolDecodingRules.CommandInfo> commands = validateCommands(raw.commands(), prefix + "commands");
            rules.add(new ProtocolDecodingRules(key, name, timing, frame, commands));
        }
        return List.copyOf(rules);
    }

    private List<RemoteProfile> validateRemotes(
            List<ParserConfigurationDocument.RemoteEntry> entries,
            Map<String, ProtocolDecodingRules> ruleByKey) {
        uniqueKeys(entries.stream().map(entry -> entry == null ? null : entry.key()).toList(), "remoteConfigs");
        List<RemoteProfile> remotes = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ParserConfigurationDocument.RemoteEntry raw = entries.get(index);
            if (raw == null) {
                throw invalid("remoteConfigs[" + index + "] 不能为空");
            }
            String prefix = "remoteConfigs[" + index + "].";
            String key = requireText(raw.key(), prefix + "key");
            String name = requireText(raw.name(), prefix + "name");
            List<Integer> remoteId = parseHex(raw.remoteId(), 3, prefix + "remoteId");
            Charset charset = parseCharset(raw.charset(), prefix + "charset");
            BigDecimal idleThreshold = requirePositive(raw.idleThresholdMs(), prefix + "idleThresholdMs");
            if (raw.idleLevel() == null || (raw.idleLevel() != 0 && raw.idleLevel() != 1)) {
                throw invalid(prefix + "idleLevel 只能是 0 或 1");
            }
            SignalLevelMapping mapping;
            try {
                mapping = SignalLevelMapping.parse(requireText(raw.levelMapping(), prefix + "levelMapping"));
            } catch (IllegalArgumentException exception) {
                throw invalid(prefix + "levelMapping 只能是 direct 或 inverted");
            }
            String ruleKey = requireText(raw.protocolRuleKey(), prefix + "protocolRuleKey");
            if (!ruleByKey.containsKey(ruleKey)) {
                throw invalid(prefix + "protocolRuleKey 关联规则不存在: " + ruleKey);
            }
            remotes.add(new RemoteProfile(
                    key, name, remoteId, charset, idleThreshold, raw.idleLevel(), mapping, ruleKey));
        }
        return List.copyOf(remotes);
    }

    private ProtocolDecodingRules.Timing validateTiming(
            ParserConfigurationDocument.TimingEntry raw,
            String prefix) {
        if (raw == null) {
            throw invalid(prefix.substring(0, prefix.length() - 1) + " 不能为空");
        }
        BigDecimal nominal = requirePositive(raw.nominalBitPeriodUs(), prefix + "nominalBitPeriodUs");
        BigDecimal prefilter = requirePositive(raw.prefilterToleranceRatio(), prefix + "prefilterToleranceRatio");
        if (prefilter.compareTo(BigDecimal.ONE) >= 0) {
            throw invalid(prefix + "prefilterToleranceRatio 必须小于 1");
        }
        int minimumPulses = requirePositiveInteger(raw.minValidPulseCount(), prefix + "minValidPulseCount");
        int maximumPulses = requirePositiveInteger(raw.maxInitialFitPulseCount(), prefix + "maxInitialFitPulseCount");
        if (maximumPulses < minimumPulses) {
            throw invalid(prefix + "maxInitialFitPulseCount 不能小于 minValidPulseCount");
        }
        BigDecimal residual = requirePositive(raw.maxFitResidualRatio(), prefix + "maxFitResidualRatio");
        BigDecimal tail = requirePositive(raw.tailEdgeToleranceRatio(), prefix + "tailEdgeToleranceRatio");
        if (tail.compareTo(new BigDecimal("0.5")) >= 0) {
            throw invalid(prefix + "tailEdgeToleranceRatio 必须小于 0.5");
        }
        int phases = requirePositiveInteger(raw.phaseCount(), prefix + "phaseCount");
        if (phases < 2) {
            throw invalid(prefix + "phaseCount 必须大于等于 2");
        }
        BigDecimal dedup = requirePositive(raw.dedupToleranceRatio(), prefix + "dedupToleranceRatio");
        return new ProtocolDecodingRules.Timing(
                nominal, prefilter, minimumPulses, maximumPulses, residual, tail, phases, dedup);
    }

    private ProtocolDecodingRules.Frame validateFrame(
            ParserConfigurationDocument.FrameEntry raw,
            String prefix) {
        if (raw == null) {
            throw invalid(prefix.substring(0, prefix.length() - 1) + " 不能为空");
        }
        String marker = normalizeHex(raw.syncMarkerHex(), 3, prefix + "syncMarkerHex");
        String header = normalizeHex(raw.formalHeaderHex(), 2, prefix + "formalHeaderHex");
        String tail = normalizeHex(raw.tailHex(), 2, prefix + "tailHex");
        requireFixed(marker, SYNC_MARKER, prefix + "syncMarkerHex");
        requireFixed(header, FORMAL_HEADER, prefix + "formalHeaderHex");
        requireFixed(raw.formalFrameBits(), 104, prefix + "formalFrameBits");
        requireFixed(raw.remoteIdByteOffset(), 6, prefix + "remoteIdByteOffset");
        requireFixed(raw.commandByteOffset(), 10, prefix + "commandByteOffset");
        requireFixed(raw.tailByteOffset(), 11, prefix + "tailByteOffset");
        requireFixed(tail, TAIL, prefix + "tailHex");
        if (!marker.endsWith(header)) {
            throw invalid(prefix + "syncMarkerHex 末尾必须等于 formalHeaderHex");
        }
        return new ProtocolDecodingRules.Frame(marker, header, 104, 6, 10, 11, tail);
    }

    private List<ProtocolDecodingRules.CommandInfo> validateCommands(
            List<ParserConfigurationDocument.CommandEntry> entries,
            String fieldName) {
        if (entries == null || entries.isEmpty()) {
            throw invalid(fieldName + " 不能为空");
        }
        Set<Integer> codes = new HashSet<>();
        List<ProtocolDecodingRules.CommandInfo> commands = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            ParserConfigurationDocument.CommandEntry raw = entries.get(index);
            String prefix = fieldName + "[" + index + "].";
            if (raw == null) {
                throw invalid(prefix.substring(0, prefix.length() - 1) + " 不能为空");
            }
            int code = parseHex(raw.code(), 1, prefix + "code").get(0);
            if (!codes.add(code)) {
                throw invalid(fieldName + " 命令码不能重复: " + normalizeHex(raw.code(), 1, prefix + "code"));
            }
            commands.add(new ProtocolDecodingRules.CommandInfo(
                    code,
                    requireText(raw.channel(), prefix + "channel"),
                    requireText(raw.action(), prefix + "action")));
        }
        return List.copyOf(commands);
    }

    private Set<String> uniqueKeys(List<String> values, String fieldName) {
        Set<String> keys = new HashSet<>();
        for (String value : values) {
            String key = requireText(value, fieldName + "[].key");
            if (!keys.add(key)) {
                throw invalid(fieldName + " key 不能重复: " + key);
            }
        }
        return Set.copyOf(keys);
    }

    private String normalizeHex(String value, int byteCount, String fieldName) {
        return parseHex(value, byteCount, fieldName).stream()
                .map(number -> "%02X".formatted(number))
                .collect(Collectors.joining(" "));
    }

    private List<Integer> parseHex(String value, int byteCount, String fieldName) {
        String text = requireText(value, fieldName);
        String[] parts = text.split("\\s+");
        if (parts.length != byteCount) {
            throw invalid(fieldName + " 必须是 " + byteCount + " 个十六进制字节");
        }
        List<Integer> bytes = new ArrayList<>(byteCount);
        for (String part : parts) {
            if (!part.matches("[0-9A-Fa-f]{2}")) {
                throw invalid(fieldName + " 必须是 " + byteCount + " 个十六进制字节");
            }
            bytes.add(Integer.parseInt(part, 16));
        }
        return List.copyOf(bytes);
    }

    private Charset parseCharset(String value, String fieldName) {
        String text = requireText(value, fieldName);
        try {
            return Charset.forName(text);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            throw invalid(fieldName + " 不是当前 JDK 支持的字符集: " + text);
        }
    }

    private BigDecimal requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw invalid(fieldName + " 必须大于 0");
        }
        return value;
    }

    private int requirePositiveInteger(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw invalid(fieldName + " 必须是正整数");
        }
        return value;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private void requireFixed(Object actual, Object expected, String fieldName) {
        if (!expected.equals(actual)) {
            throw invalid(fieldName + " 必须固定为 " + expected);
        }
    }

    private ParserConfigurationException invalid(String message) {
        return new ParserConfigurationException(ParserErrorCode.CONFIG_INVALID, message);
    }
}
