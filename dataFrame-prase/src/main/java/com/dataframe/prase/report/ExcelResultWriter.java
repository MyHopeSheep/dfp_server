package com.dataframe.prase.report;

import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.FrameResult;
import com.dataframe.prase.model.ParseOutcome;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExcelResultWriter {

    private static final int AUDIT_START_COLUMN = 27;
    private static final int AUDIT_END_COLUMN = 42;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] DETAIL_HEADERS = {
            "前导1", "前导2", "前导3", "前导4", "前导5", "前导6",
            "前导7", "前导8", "前导9", "前导10", "前导11", "前导12",
            "帧头1", "帧头2",
            "加密1", "加密2", "加密3", "加密4",
            "ID1", "ID2", "ID3",
            "射频数据", "命令", "尾帧1", "尾帧2",
            "遥控器通道", "动作",
            "帧序号", "候选区段序号", "起始时间[s]", "结束时间[s]", "识别结果", "失败原因",
            "bit 周期[us]", "起始相位[us]", "核心帧 bit 起始偏移", "恢复出的 bit 位串",
            "恢复出的字节串", "加密字段（4 字节）", "遥控器 ID（3 字节）", "射频数据（1 字节）",
            "命令字节", "命令说明"
    };

    public void write(ParseOutcome outcome) throws IOException {
        Path output = outcome.output();
        Path parent = output.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Workbook workbook = new XSSFWorkbook()) {
                Styles styles = createStyles(workbook);
                writeResultsSheet(workbook, outcome, styles);
                writeSummarySheet(workbook, outcome, styles);
                try (OutputStream stream = Files.newOutputStream(
                        output,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
                    workbook.write(stream);
                }
            }
        } catch (IOException exception) {
            throw new IOException("Excel 工作簿写入失败: " + output, exception);
        }
    }

    private void writeResultsSheet(Workbook workbook, ParseOutcome outcome, Styles styles) {
        Sheet sheet = workbook.createSheet("解析结果");
        Row groupHeader = sheet.createRow(0);
        Row detailHeader = sheet.createRow(1);
        writeGroupHeaders(sheet, groupHeader, styles.groupHeader());
        for (int column = 0; column < DETAIL_HEADERS.length; column++) {
            setText(detailHeader, column, DETAIL_HEADERS[column], styles.detailHeader());
        }
        sheet.createFreezePane(0, 2);

        int rowIndex = 2;
        for (FrameResult frame : outcome.frames()) {
            writeFrameRow(sheet.createRow(rowIndex++), frame, styles);
        }
        setResultColumnWidths(sheet);
    }

    private void writeGroupHeaders(Sheet sheet, Row row, CellStyle style) {
        mergeAndSet(sheet, row, 0, 11, "前导", style);
        mergeAndSet(sheet, row, 12, 13, "帧头", style);
        mergeAndSet(sheet, row, 14, 17, "加密", style);
        mergeAndSet(sheet, row, 18, 20, "ID", style);
        setText(row, 21, "射频数据", style);
        setText(row, 22, "通道命令", style);
        mergeAndSet(sheet, row, 23, 24, "尾帧", style);
        mergeAndSet(sheet, row, 25, 26, "遥控器和动作", style);
        mergeAndSet(sheet, row, AUDIT_START_COLUMN, AUDIT_END_COLUMN, "解析审计", style);
    }

    private void writeFrameRow(Row row, FrameResult frame, Styles styles) {
        for (int column = 0; column <= 10; column++) {
            setText(row, column, "未采集", styles.data());
        }
        for (int column = 11; column <= 24; column++) {
            int coreByteIndex = column - 11;
            String value = protocolByte(frame, coreByteIndex);
            setText(row, column, value, styles.data());
        }
        setText(row, 25, frame.channelDescription(), styles.data());
        setText(row, 26, frame.actionDescription(), styles.data());

        setNumber(row, 27, frame.frameNumber(), styles.data());
        setNumber(row, 28, frame.segmentNumber(), styles.data());
        setDecimal(row, 29, frame.startSeconds(), styles.timeData());
        setDecimal(row, 30, frame.endSeconds(), styles.timeData());
        setText(row, 31, frame.recognitionResult(), styles.data());
        setText(row, 32, frame.failureReasonText(), styles.data());
        setDecimal(row, 33, frame.bitPeriodUs(), styles.decimalData());
        setDecimal(row, 34, frame.phaseUs(), styles.decimalData());
        if (frame.coreStartBitOffset() >= 0) {
            setNumber(row, 35, frame.coreStartBitOffset(), styles.data());
        } else {
            setText(row, 35, "未定位", styles.data());
        }
        setText(row, 36, frame.recoveredBits(), styles.auditText());
        setText(row, 37, frame.recoveredByteString(), styles.auditText());
        setText(row, 38, byteRange(frame, 3, 7), styles.data());
        setText(row, 39, byteRange(frame, 7, 10), styles.data());
        setText(row, 40, byteAt(frame, 10), styles.data());
        setText(row, 41, byteAt(frame, 11), styles.data());
        setText(row, 42, frame.channelDescription() + " / " + frame.actionDescription(), styles.data());
    }

    private String protocolByte(FrameResult frame, int coreByteIndex) {
        if (!frame.headerLocated() || coreByteIndex >= frame.recoveredBytes().size()) {
            return "未恢复";
        }
        return "0x" + FrameResult.hex(frame.recoveredBytes().get(coreByteIndex));
    }

    private String byteAt(FrameResult frame, int index) {
        if (!frame.headerLocated() || index >= frame.recoveredBytes().size()) {
            return "未恢复";
        }
        return FrameResult.hex(frame.recoveredBytes().get(index));
    }

    private String byteRange(FrameResult frame, int fromIndex, int toIndex) {
        if (!frame.headerLocated() || frame.recoveredBytes().size() < toIndex) {
            return "未恢复";
        }
        return frame.recoveredBytes().subList(fromIndex, toIndex).stream()
                .map(FrameResult::hex)
                .reduce((left, right) -> left + " " + right)
                .orElse("未恢复");
    }

    private void writeSummarySheet(Workbook workbook, ParseOutcome outcome, Styles styles) {
        Sheet sheet = workbook.createSheet("处理摘要");
        int rowIndex = 0;
        rowIndex = summaryRow(sheet, rowIndex, "输入文件", outcome.input().toString(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "读取边沿数量", outcome.edgeCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "候选区段数量", outcome.segmentCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "有效帧数量", outcome.validFrameCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "非有效帧数量", outcome.invalidFrameCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "文件边界残片数量", outcome.boundaryFragments().size(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "遥控器 ID", hexList(outcome.remoteId()), styles);
        rowIndex = summaryRow(sheet, rowIndex, "bit 周期[us]", outcome.bitPeriodUs(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "相位步长[us]", outcome.phaseStepUs(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "空闲阈值[ms]", outcome.idleThresholdMs(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "空闲电平", outcome.idleLevel(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "电平映射", outcome.levelMapping().description(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "处理时间", outcome.processedAt().format(TIME_FORMATTER), styles);

        Row fragmentTitle = sheet.createRow(rowIndex++);
        setText(fragmentTitle, 0, "文件边界残片", styles.groupHeader());
        sheet.addMergedRegion(new CellRangeAddress(fragmentTitle.getRowNum(), fragmentTitle.getRowNum(), 0, 3));
        Row fragmentHeader = sheet.createRow(rowIndex++);
        setText(fragmentHeader, 0, "序号", styles.detailHeader());
        setText(fragmentHeader, 1, "起始时间[s]", styles.detailHeader());
        setText(fragmentHeader, 2, "结束时间[s]", styles.detailHeader());
        setText(fragmentHeader, 3, "原因", styles.detailHeader());

        int fragmentNumber = 1;
        for (BoundaryFragment fragment : outcome.boundaryFragments()) {
            Row row = sheet.createRow(rowIndex++);
            setNumber(row, 0, fragmentNumber++, styles.data());
            setDecimal(row, 1, fragment.startSeconds(), styles.timeData());
            setDecimal(row, 2, fragment.endSeconds(), styles.timeData());
            setText(row, 3, fragment.reason(), styles.data());
        }
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 28 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        sheet.setColumnWidth(3, 52 * 256);
    }

    private int summaryRow(Sheet sheet, int rowIndex, String key, Object value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        setText(row, 0, key, styles.detailHeader());
        if (value instanceof BigDecimal decimal) {
            setDecimal(row, 1, decimal, styles.decimalData());
        } else if (value instanceof Number number) {
            setNumber(row, 1, number.doubleValue(), styles.data());
        } else {
            setText(row, 1, String.valueOf(value), styles.data());
        }
        return rowIndex + 1;
    }

    private String hexList(List<Integer> values) {
        return values.stream()
                .map(FrameResult::hex)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private void mergeAndSet(Sheet sheet, Row row, int fromColumn, int toColumn, String text, CellStyle style) {
        setText(row, fromColumn, text, style);
        for (int column = fromColumn + 1; column <= toColumn; column++) {
            setText(row, column, "", style);
        }
        sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), fromColumn, toColumn));
    }

    private void setText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setNumber(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setDecimal(Row row, int column, BigDecimal value, CellStyle style) {
        setNumber(row, column, value.doubleValue(), style);
    }

    private void setResultColumnWidths(Sheet sheet) {
        for (int column = 0; column <= 26; column++) {
            sheet.setColumnWidth(column, column >= 25 ? 16 * 256 : 12 * 256);
        }
        for (int column = AUDIT_START_COLUMN; column <= AUDIT_END_COLUMN; column++) {
            int width = switch (column) {
                case 32, 36, 37 -> 48;
                case 29, 30, 35, 38, 39, 42 -> 24;
                default -> 16;
            };
            sheet.setColumnWidth(column, width * 256);
        }
    }

    private Styles createStyles(Workbook workbook) {
        Font normalFont = workbook.createFont();
        normalFont.setFontName("宋体");
        Font boldFont = workbook.createFont();
        boldFont.setFontName("宋体");
        boldFont.setBold(true);

        CellStyle groupHeader = baseStyle(workbook, boldFont);
        groupHeader.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        groupHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle detailHeader = baseStyle(workbook, boldFont);
        detailHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        detailHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle data = baseStyle(workbook, normalFont);
        CellStyle timeData = baseStyle(workbook, normalFont);
        timeData.setDataFormat(workbook.createDataFormat().getFormat("0.0000000"));
        CellStyle decimalData = baseStyle(workbook, normalFont);
        decimalData.setDataFormat(workbook.createDataFormat().getFormat("0.########"));
        CellStyle auditText = baseStyle(workbook, normalFont);
        auditText.setWrapText(true);
        return new Styles(groupHeader, detailHeader, data, timeData, decimalData, auditText);
    }

    private CellStyle baseStyle(Workbook workbook, Font font) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private record Styles(
            CellStyle groupHeader,
            CellStyle detailHeader,
            CellStyle data,
            CellStyle timeData,
            CellStyle decimalData,
            CellStyle auditText) {
    }
}
