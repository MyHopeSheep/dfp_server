package com.dataframe.prase.csv;

import com.dataframe.prase.model.EdgeRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CsvEdgeReader {

    public List<EdgeRecord> read(Path input, Charset charset) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(charset, "charset");
        if (!Files.exists(input)) {
            throw new IOException("输入文件不存在: " + input);
        }
        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new IOException("输入文件无法读取: " + input);
        }

        try (BufferedReader reader = Files.newBufferedReader(input, charset)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("CSV 表头至少两列");
            }
            String[] headerColumns = splitColumns(header);
            if (headerColumns.length < 2) {
                throw new IllegalArgumentException("CSV 表头至少两列");
            }
            if (headerColumns[0].isBlank() || headerColumns[1].isBlank()) {
                throw new IllegalArgumentException("CSV 前两列表头不能为空");
            }

            List<EdgeRecord> records = new ArrayList<>();
            BigDecimal previousTime = null;
            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] columns = splitColumns(line);
                if (columns.length < 2) {
                    throw lineError(lineNumber, "CSV 数据少于两列");
                }
                BigDecimal time = parseTime(columns[0], lineNumber);
                int level = parseLevel(columns[1], lineNumber);
                if (previousTime != null && time.compareTo(previousTime) < 0) {
                    throw lineError(lineNumber, "时间顺序倒退");
                }
                records.add(new EdgeRecord(time, level, lineNumber));
                previousTime = time;
            }
            return List.copyOf(records);
        }
    }

    private String[] splitColumns(String line) {
        return line.split(",", -1);
    }

    private BigDecimal parseTime(String value, long lineNumber) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw lineError(lineNumber, "时间值无法解析", exception);
        }
    }

    private int parseLevel(String value, long lineNumber) {
        String normalized = value.trim();
        if (!"0".equals(normalized) && !"1".equals(normalized)) {
            throw lineError(lineNumber, "电平只能是 0 或 1");
        }
        return Integer.parseInt(normalized);
    }

    private IllegalArgumentException lineError(long lineNumber, String message) {
        return new IllegalArgumentException("第 " + lineNumber + " 行: " + message);
    }

    private IllegalArgumentException lineError(long lineNumber, String message, Throwable cause) {
        return new IllegalArgumentException("第 " + lineNumber + " 行: " + message, cause);
    }
}
