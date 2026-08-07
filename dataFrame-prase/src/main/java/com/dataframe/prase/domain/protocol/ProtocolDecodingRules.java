package com.dataframe.prase.domain.protocol;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ProtocolDecodingRules(
        String key,
        String name,
        Timing timing,
        Frame frame,
        List<CommandInfo> commands) {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final CommandInfo UNKNOWN_COMMAND = new CommandInfo(-1, "未知通道", "未知命令");

    public ProtocolDecodingRules {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(timing, "timing");
        Objects.requireNonNull(frame, "frame");
        commands = List.copyOf(commands);
    }

    public CommandInfo commandInfo(int code) {
        int normalized = code & 0xFF;
        return commands.stream()
                .filter(command -> command.code() == normalized)
                .findFirst()
                .orElse(UNKNOWN_COMMAND);
    }

    public boolean isKnownCommand(int code) {
        return commandInfo(code) != UNKNOWN_COMMAND;
    }

    public Derived derived() {
        BigDecimal one = BigDecimal.ONE;
        BigDecimal minimum = timing.nominalBitPeriodUs()
                .multiply(one.subtract(timing.prefilterToleranceRatio()), MATH_CONTEXT);
        BigDecimal maximum = timing.nominalBitPeriodUs()
                .multiply(one.add(timing.prefilterToleranceRatio()), MATH_CONTEXT);
        List<Integer> markerBits = frame.syncMarkerBits();
        List<Integer> transitions = new ArrayList<>();
        transitions.add(0);
        for (int index = 1; index < markerBits.size(); index++) {
            if (!markerBits.get(index).equals(markerBits.get(index - 1))) {
                transitions.add(index);
            }
        }
        int formalFrameBytes = frame.formalFrameBits() / Byte.SIZE;
        int syncPreludeBytes = frame.syncMarkerBytes().size() - frame.formalHeaderBytes().size();
        int syncPreludeBits = syncPreludeBytes * Byte.SIZE;
        int syncInclusiveBits = frame.formalFrameBits() + syncPreludeBits;
        return new Derived(
                minimum,
                maximum,
                timing.maxFitResidualRatio().multiply(BigDecimal.valueOf(2), MATH_CONTEXT),
                formalFrameBytes,
                syncPreludeBits,
                syncPreludeBytes,
                syncInclusiveBits,
                syncInclusiveBits / Byte.SIZE,
                transitions,
                "T_est / " + timing.phaseCount());
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

        public List<Integer> syncMarkerBytes() {
            return parseHex(syncMarkerHex);
        }

        public List<Integer> formalHeaderBytes() {
            return parseHex(formalHeaderHex);
        }

        public List<Integer> tailBytes() {
            return parseHex(tailHex);
        }

        public List<Integer> syncMarkerBits() {
            List<Integer> bits = new ArrayList<>();
            for (int value : syncMarkerBytes()) {
                for (int shift = 7; shift >= 0; shift--) {
                    bits.add((value >>> shift) & 1);
                }
            }
            return List.copyOf(bits);
        }

        private static List<Integer> parseHex(String text) {
            return List.of(text.split(" ")).stream()
                    .map(value -> Integer.parseInt(value, 16))
                    .toList();
        }
    }

    public record CommandInfo(int code, String channel, String action) {
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
