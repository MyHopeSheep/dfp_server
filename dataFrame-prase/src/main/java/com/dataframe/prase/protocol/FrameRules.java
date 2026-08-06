package com.dataframe.prase.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FrameRules {

    private static final CommandInfo UNKNOWN_COMMAND = new CommandInfo("未知通道", "未知命令");
    private static final Map<Integer, CommandInfo> COMMANDS = Map.ofEntries(
            Map.entry(0x01, new CommandInfo("通道1", "停")),
            Map.entry(0x02, new CommandInfo("通道1", "上")),
            Map.entry(0x04, new CommandInfo("通道1", "下")),
            Map.entry(0x21, new CommandInfo("通道2", "停")),
            Map.entry(0x22, new CommandInfo("通道2", "上")),
            Map.entry(0x24, new CommandInfo("通道2", "下")),
            Map.entry(0x41, new CommandInfo("通道3", "停")),
            Map.entry(0x42, new CommandInfo("通道3", "上")),
            Map.entry(0x44, new CommandInfo("通道3", "下")),
            Map.entry(0x61, new CommandInfo("通道4", "停")),
            Map.entry(0x62, new CommandInfo("通道4", "上")),
            Map.entry(0x64, new CommandInfo("通道4", "下")),
            Map.entry(0x68, new CommandInfo("通道4", "上+停")),
            Map.entry(0x63, new CommandInfo("通道4", "下+停")),
            Map.entry(0x66, new CommandInfo("通道4", "停长按 15 秒")),
            Map.entry(0x67, new CommandInfo("通道4", "停长按 5 秒")));

    public ValidationResult validate(List<Integer> recoveredBytes, List<Integer> expectedRemoteId) {
        Objects.requireNonNull(recoveredBytes, "recoveredBytes");
        Objects.requireNonNull(expectedRemoteId, "expectedRemoteId");
        if (expectedRemoteId.size() != 3) {
            throw new IllegalArgumentException("遥控器 ID 必须是 3 个字节");
        }

        boolean legacySyncInclusive = recoveredBytes.size() >= 3
                && recoveredBytes.subList(0, 3).equals(List.of(0xAA, 0x2D, 0xD4));
        List<Integer> formalBytes = legacySyncInclusive
                ? recoveredBytes.subList(1, recoveredBytes.size())
                : recoveredBytes;

        List<String> failures = new ArrayList<>();
        boolean complete = formalBytes.size() >= 13;
        if (!complete) {
            failures.add(legacySyncInclusive ? "核心帧数据截断" : "正式帧数据截断");
        }

        if (complete && !formalBytes.subList(0, 2).equals(List.of(0x2D, 0xD4))) {
            failures.add("帧头不匹配");
        }
        if (complete && !formalBytes.subList(6, 9).equals(expectedRemoteId)) {
            failures.add("遥控器 ID 不匹配");
        }

        Integer command = formalBytes.size() > 10 ? formalBytes.get(10) : null;
        CommandInfo commandInfo = command == null ? UNKNOWN_COMMAND : commandInfo(command);
        if (complete && !COMMANDS.containsKey(command)) {
            failures.add("命令字节未知");
        }
        if (complete && (formalBytes.get(11) != 0xAA || formalBytes.get(12) != 0xAA)) {
            failures.add("尾帧不匹配");
        }
        return new ValidationResult(complete, failures.isEmpty(), failures, commandInfo);
    }

    public static CommandInfo commandInfo(int command) {
        return COMMANDS.getOrDefault(command & 0xFF, UNKNOWN_COMMAND);
    }

    public record CommandInfo(String channel, String action) {
    }

    public record ValidationResult(
            boolean complete,
            boolean valid,
            List<String> failureReasons,
            CommandInfo commandInfo) {
        public ValidationResult {
            failureReasons = List.copyOf(failureReasons);
        }
    }
}
