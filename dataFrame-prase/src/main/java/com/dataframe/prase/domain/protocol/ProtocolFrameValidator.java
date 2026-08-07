package com.dataframe.prase.domain.protocol;

import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProtocolFrameValidator {

    private final ProtocolDecodingRules ruleSet;

    public ProtocolFrameValidator(ProtocolDecodingRules ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
    }

    public ValidationResult validate(List<Integer> recoveredBytes, List<Integer> expectedRemoteId) {
        Objects.requireNonNull(recoveredBytes, "recoveredBytes");
        Objects.requireNonNull(expectedRemoteId, "expectedRemoteId");
        if (expectedRemoteId.size() != 3) {
            throw new IllegalArgumentException("遥控器 ID 必须是 3 个字节");
        }

        List<Integer> marker = ruleSet.frame().syncMarkerBytes();
        boolean legacySyncInclusive = recoveredBytes.size() >= marker.size()
                && recoveredBytes.subList(0, marker.size()).equals(marker);
        List<Integer> formalBytes = legacySyncInclusive
                ? recoveredBytes.subList(marker.size() - ruleSet.frame().formalHeaderBytes().size(), recoveredBytes.size())
                : recoveredBytes;

        List<String> failures = new ArrayList<>();
        int formalBytesRequired = ruleSet.derived().formalFrameBytes();
        boolean complete = formalBytes.size() >= formalBytesRequired;
        if (!complete) {
            failures.add(legacySyncInclusive ? "核心帧数据截断" : "正式帧数据截断");
        }

        if (complete && !formalBytes.subList(0, ruleSet.frame().formalHeaderBytes().size())
                .equals(ruleSet.frame().formalHeaderBytes())) {
            failures.add("帧头不匹配");
        }
        int idOffset = ruleSet.frame().remoteIdByteOffset();
        if (complete && !formalBytes.subList(idOffset, idOffset + expectedRemoteId.size())
                .equals(expectedRemoteId)) {
            failures.add("遥控器 ID 不匹配");
        }

        int commandOffset = ruleSet.frame().commandByteOffset();
        Integer command = formalBytes.size() > commandOffset ? formalBytes.get(commandOffset) : null;
        ProtocolDecodingRules.CommandInfo commandInfo = command == null
                ? ruleSet.commandInfo(-1)
                : ruleSet.commandInfo(command);
        if (complete && !ruleSet.isKnownCommand(command)) {
            failures.add("命令字节未知");
        }
        int tailOffset = ruleSet.frame().tailByteOffset();
        if (complete && !formalBytes.subList(tailOffset, tailOffset + ruleSet.frame().tailBytes().size())
                .equals(ruleSet.frame().tailBytes())) {
            failures.add("尾帧不匹配");
        }
        return new ValidationResult(complete, failures.isEmpty(), failures, commandInfo);
    }

    public record ValidationResult(
            boolean complete,
            boolean valid,
            List<String> failureReasons,
            ProtocolDecodingRules.CommandInfo commandInfo) {
        public ValidationResult {
            failureReasons = List.copyOf(failureReasons);
        }
    }
}
