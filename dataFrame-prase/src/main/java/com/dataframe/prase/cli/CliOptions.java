package com.dataframe.prase.cli;

import com.dataframe.prase.signal.LevelMapping;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CliOptions(
        Path input,
        List<Integer> remoteId,
        Path output,
        Charset charset,
        BigDecimal bitPeriodUs,
        BigDecimal phaseStepUs,
        BigDecimal idleThresholdMs,
        int idleLevel,
        BigDecimal dedupToleranceUs,
        LevelMapping levelMapping,
        boolean help) {

    private static final BigDecimal DEFAULT_BIT_PERIOD_US = new BigDecimal("416.67");
    private static final BigDecimal DEFAULT_IDLE_THRESHOLD_MS = new BigDecimal("40");
    private static final Charset DEFAULT_CHARSET = Charset.forName("GBK");
    private static final Pattern COUNTER_SUFFIX = Pattern.compile("^(.*)\\((\\d+)\\)$");
    private static final Set<String> VALUE_ARGUMENTS = Set.of(
            "--input",
            "--remote-id",
            "--output",
            "--charset",
            "--bit-period-us",
            "--phase-step-us",
            "--idle-threshold-ms",
            "--idle-level",
            "--dedup-tolerance-us",
            "--level-mapping");

    public CliOptions {
        remoteId = List.copyOf(remoteId);
        Objects.requireNonNull(levelMapping, "levelMapping");
    }

    public static CliOptions parse(String[] args) {
        Objects.requireNonNull(args, "args");
        Map<String, String> values = new LinkedHashMap<>();
        boolean help = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--help".equals(argument)) {
                if (help) {
                    throw new IllegalArgumentException("重复参数: --help");
                }
                help = true;
                continue;
            }
            if (!VALUE_ARGUMENTS.contains(argument)) {
                throw new IllegalArgumentException("未知参数: " + argument);
            }
            if (values.containsKey(argument)) {
                throw new IllegalArgumentException("重复参数: " + argument);
            }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("参数缺少值: " + argument);
            }
            values.put(argument, args[++index]);
        }

        BigDecimal bitPeriodUs = positiveDecimal(
                values.getOrDefault("--bit-period-us", DEFAULT_BIT_PERIOD_US.toPlainString()),
                "--bit-period-us");
        BigDecimal phaseStepUs = values.containsKey("--phase-step-us")
                ? positiveDecimal(values.get("--phase-step-us"), "--phase-step-us")
                : bitPeriodUs.divide(BigDecimal.valueOf(16));
        BigDecimal idleThresholdMs = positiveDecimal(
                values.getOrDefault("--idle-threshold-ms", DEFAULT_IDLE_THRESHOLD_MS.toPlainString()),
                "--idle-threshold-ms");
        int idleLevel = parseIdleLevel(values.getOrDefault("--idle-level", "0"));
        BigDecimal dedupToleranceUs = values.containsKey("--dedup-tolerance-us")
                ? positiveDecimal(values.get("--dedup-tolerance-us"), "--dedup-tolerance-us")
                : bitPeriodUs.divide(BigDecimal.valueOf(2));
        LevelMapping levelMapping = LevelMapping.parse(
                values.getOrDefault("--level-mapping", LevelMapping.INVERTED.cliValue()));
        Charset charset = parseCharset(values.getOrDefault("--charset", DEFAULT_CHARSET.name()));

        if (help) {
            return new CliOptions(
                    null,
                    List.of(),
                    null,
                    charset,
                    bitPeriodUs,
                    phaseStepUs,
                    idleThresholdMs,
                    idleLevel,
                    dedupToleranceUs,
                    levelMapping,
                    true);
        }

        Path input = Path.of(required(values, "--input"));
        List<Integer> remoteId = parseRemoteId(required(values, "--remote-id"));
        Path requestedOutput = values.containsKey("--output")
                ? Path.of(values.get("--output"))
                : defaultOutput(input);
        Path output = resolveOutputPath(input, requestedOutput);

        return new CliOptions(
                input,
                remoteId,
                output,
                charset,
                bitPeriodUs,
                phaseStepUs,
                idleThresholdMs,
                idleLevel,
                dedupToleranceUs,
                levelMapping,
                false);
    }

    public static String helpText() {
        return """
                用法:
                  java -jar dataFrame-prase.jar --input <CSV文件> --remote-id "B0 42 87" [选项]

                必填参数:
                  --input <路径>                 输入 CSV 文件
                  --remote-id <十六进制字节>     3 字节遥控器 ID

                可选参数:
                  --output <路径>                输出 Excel 路径；默认使用输入目录下的 data_praseResult
                                                 同名文件已存在时追加 (1)、(2) 等编号
                  --charset <字符集>             默认 GBK
                  --bit-period-us <数值>         默认 416.67
                  --phase-step-us <数值>         默认 bit 周期 / 16
                  --idle-threshold-ms <数值>     默认 40
                  --idle-level <0|1>             默认 0
                  --dedup-tolerance-us <数值>    默认 bit 周期 / 2
                  --level-mapping <模式>         direct 或 inverted，默认 inverted
                  --help                         显示帮助
                """;
    }

    private static String required(Map<String, String> values, String argument) {
        String value = values.get(argument);
        if (value == null || value.isBlank()) {
            if ("--remote-id".equals(argument)) {
                throw new IllegalArgumentException("缺少遥控器 Id 配置（--remote-id）");
            }
            throw new IllegalArgumentException("缺少必填参数: " + argument);
        }
        return value;
    }

    private static List<Integer> parseRemoteId(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("--remote-id 必须包含 3 个十六进制字节");
        }
        try {
            return List.of(parseHexByte(parts[0]), parseHexByte(parts[1]), parseHexByte(parts[2]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--remote-id 必须包含 3 个十六进制字节", exception);
        }
    }

    private static int parseHexByte(String value) {
        if (!value.matches("[0-9A-Fa-f]{2}")) {
            throw new NumberFormatException(value);
        }
        return Integer.parseInt(value, 16);
    }

    private static BigDecimal positiveDecimal(String value, String argument) {
        try {
            BigDecimal number = new BigDecimal(value);
            if (number.signum() <= 0) {
                throw new IllegalArgumentException(argument + " 必须大于 0");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(argument + " 必须是有效数字", exception);
        }
    }

    private static int parseIdleLevel(String value) {
        if (!"0".equals(value) && !"1".equals(value)) {
            throw new IllegalArgumentException("--idle-level 只能是 0 或 1");
        }
        return Integer.parseInt(value);
    }

    private static Charset parseCharset(String value) {
        try {
            return Charset.forName(value);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            throw new IllegalArgumentException("--charset 不受支持: " + value, exception);
        }
    }

    private static Path defaultOutput(Path input) {
        Path absoluteInput = input.toAbsolutePath().normalize();
        Path inputDirectory = absoluteInput.getParent();
        if (inputDirectory == null || inputDirectory.getFileName() == null) {
            throw new IllegalArgumentException("无法推导默认结果目录: " + input);
        }
        Path outputDirectory = inputDirectory.resolve("data_praseResult");
        try {
            Files.createDirectories(outputDirectory);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("默认结果目录创建失败: " + outputDirectory, exception);
        }

        String fileName = absoluteInput.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return outputDirectory.resolve(baseName + "-解析结果.xlsx");
    }

    private static Path resolveOutputPath(Path input, Path requestedOutput) {
        Path normalizedInput = input.toAbsolutePath().normalize();
        Path normalizedOutput = requestedOutput.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            return requestedOutput;
        }
        if (Files.isDirectory(requestedOutput)) {
            throw new IllegalArgumentException("输出路径不能是目录: " + requestedOutput);
        }
        if (!Files.exists(requestedOutput)) {
            return requestedOutput;
        }

        Path parent = requestedOutput.toAbsolutePath().getParent();
        String fileName = requestedOutput.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        Matcher matcher = COUNTER_SUFFIX.matcher(baseName);
        int counter = 1;
        if (matcher.matches()) {
            baseName = matcher.group(1);
            try {
                counter = Math.addExact(Integer.parseInt(matcher.group(2)), 1);
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException("输出文件编号过大: " + requestedOutput, exception);
            }
        }

        while (true) {
            Path candidate = parent.resolve(baseName + "(" + counter + ")" + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            if (counter == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("输出文件编号已耗尽: " + requestedOutput);
            }
            counter++;
        }
    }
}
