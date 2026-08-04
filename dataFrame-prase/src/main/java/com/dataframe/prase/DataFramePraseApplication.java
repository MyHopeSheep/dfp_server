package com.dataframe.prase;

import com.dataframe.prase.cli.CliOptions;
import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.ParseOutcome;
import com.dataframe.prase.service.ParseService;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

public final class DataFramePraseApplication {

    private DataFramePraseApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(error, "error");
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help()) {
                output.print(CliOptions.helpText());
                return 0;
            }

            output.println("数据帧解析开始");
            ParseOutcome outcome = new ParseService().parse(options);
            printSummary(outcome, output);
            return 0;
        } catch (IllegalArgumentException | IOException exception) {
            error.println("处理失败: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            error.println("处理失败: " + exception.getMessage());
            return 2;
        }
    }

    private static void printSummary(ParseOutcome outcome, PrintStream output) {
        output.println("输入文件: " + outcome.input());
        output.println("读取边沿数量: " + outcome.edgeCount());
        output.println("候选区段数量: " + outcome.segmentCount());
        output.println("有效帧数量: " + outcome.validFrameCount());
        output.println("非有效帧数量: " + outcome.invalidFrameCount());
        output.println("文件边界残片数量: " + outcome.boundaryFragments().size());
        for (int index = 0; index < outcome.boundaryFragments().size(); index++) {
            BoundaryFragment fragment = outcome.boundaryFragments().get(index);
            output.printf(
                    "  残片 %d: %s s - %s s, %s%n",
                    index + 1,
                    fragment.startSeconds().toPlainString(),
                    fragment.endSeconds().toPlainString(),
                    fragment.reason());
        }
        output.println("bit 周期[us]: " + outcome.bitPeriodUs().toPlainString());
        output.println("相位步长[us]: " + outcome.phaseStepUs().toPlainString());
        output.println("空闲阈值[ms]: " + outcome.idleThresholdMs().toPlainString());
        output.println("空闲电平: " + outcome.idleLevel());
        output.println("电平映射: " + outcome.levelMapping().description());
        output.println("输出文件: " + outcome.output());
    }
}
