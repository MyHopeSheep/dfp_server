package com.dataframe.prase.report;

import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.FrameAudit;
import com.dataframe.prase.model.FrameResult;
import com.dataframe.prase.model.ParseOutcome;
import com.dataframe.prase.signal.LocalPeriodEstimator;
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

    private static final int AUDIT_START_COLUMN = 16;
    private static final int AUDIT_END_COLUMN = 42;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] DETAIL_HEADERS = {
            "前导12",
            "帧头1", "帧头2",
            "加密1", "加密2", "加密3", "加密4",
            "ID1", "ID2", "ID3",
            "射频数据", "命令", "尾帧1", "尾帧2",
            "遥控器通道", "动作",
            "帧序号",
            "前导12起始跳变时间(s，原始)",
            "文件中最后关联跳变时间(s)",
            "2D帧头起始时间(s，估算)",
            "正式帧结束时间(s，估算)",
            "识别结果", "失败原因",
            "帧bit周期(us，局部估算)", "周期估计依据",
            "初始有效单bit脉宽数量", "初始拟合周期(us)", "初始拟合最大残差(us)",
            "初始拟合最大残差比", "初始拟合最大残差阈值(us)",
            "细化拟合最大残差(us)", "细化拟合最大残差比", "细化拟合最大残差阈值(us)",
            "采样相位(us，估算)", "采样相位步长(us)",
            "CSV直接恢复bit数", "最终恢复bit数",
            "最后边沿偏差(us)", "最后边沿容差(us)", "前一脉宽误差(us)",
            "帧尾恢复状态", "恢复出的bit位串", "恢复出的字节串"
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
        groupHeader.setHeightInPoints(24);
        for (int column = 0; column < DETAIL_HEADERS.length; column++) {
            setText(detailHeader, column, DETAIL_HEADERS[column], styles.detailHeader());
        }
        detailHeader.setHeightInPoints(54);
        sheet.createFreezePane(0, 2);

        int rowIndex = 2;
        for (FrameResult frame : outcome.frames()) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(42);
            writeFrameRow(row, frame, styles);
        }
        setResultColumnWidths(sheet);
    }

    private void writeGroupHeaders(Sheet sheet, Row row, CellStyle style) {
        setText(row, 0, "前导", style);
        mergeAndSet(sheet, row, 1, 2, "帧头", style);
        mergeAndSet(sheet, row, 3, 6, "加密", style);
        mergeAndSet(sheet, row, 7, 9, "ID", style);
        setText(row, 10, "射频数据", style);
        setText(row, 11, "通道命令", style);
        mergeAndSet(sheet, row, 12, 13, "尾帧", style);
        mergeAndSet(sheet, row, 14, 15, "遥控器和动作", style);
        mergeAndSet(sheet, row, AUDIT_START_COLUMN, AUDIT_END_COLUMN, "解析审计", style);
    }

    private void writeFrameRow(Row row, FrameResult frame, Styles styles) {
        setText(row, 0, frame.headerLocated() ? "0xAA" : "未恢复", styles.data());
        for (int column = 1; column <= 13; column++) {
            String value = protocolByte(frame, column - 1);
            setText(row, column, value, styles.data());
        }
        setText(row, 14, frame.channelDescription(), styles.data());
        setText(row, 15, frame.actionDescription(), styles.data());

        FrameAudit audit = frame.audit();
        setNumber(row, 16, frame.frameNumber(), styles.data());
        setDecimalOrText(row, 17, audit.preludeStartSeconds(), "不适用", styles.timeData(), styles.data());
        setDecimalOrText(row, 18, audit.lastAssociatedEdgeSeconds(), "不适用", styles.timeData(), styles.data());
        setDecimalOrText(row, 19, audit.formalStartSeconds(), "不适用", styles.timeData(), styles.data());
        setDecimalOrText(row, 20, audit.formalEndSeconds(), "不适用", styles.timeData(), styles.data());
        setText(row, 21, frame.recognitionResult(), styles.data());
        setText(row, 22, frame.failureReasonText(), styles.data());
        if (audit.refinedMaxResidualUs() == null) {
            setText(row, 23, "未估算", styles.data());
        } else {
            setDecimal(row, 23, frame.bitPeriodUs(), styles.decimalData());
        }
        setText(row, 24, audit.periodEstimationBasis(), styles.auditText());
        if (audit.initialValidSingleBitPulseCount() < 0) {
            setText(row, 25, "不适用", styles.data());
        } else {
            setNumber(row, 25, audit.initialValidSingleBitPulseCount(), styles.data());
        }
        setDecimalOrText(row, 26, audit.initialPeriodUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 27, audit.initialMaxResidualUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 28, audit.initialResidualRatio(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 29, audit.initialResidualThresholdUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 30, audit.refinedMaxResidualUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 31, audit.refinedResidualRatio(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 32, audit.refinedResidualThresholdUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 33, audit.samplePhaseUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 34, audit.phaseStepUs(), "不适用", styles.decimalData(), styles.data());
        setIntegerOrText(row, 35, audit.directRecoveredBitCount(), styles);
        setIntegerOrText(row, 36, audit.finalRecoveredBitCount(), styles);
        setDecimalOrText(row, 37, audit.tailEdgeDeviationUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 38, audit.tailEdgeToleranceUs(), "不适用", styles.decimalData(), styles.data());
        setDecimalOrText(row, 39, audit.previousPulseErrorUs(), "不适用", styles.decimalData(), styles.data());
        setText(row, 40, audit.tailRecoveryStatus(), styles.auditText());
        setText(row, 41, frame.recoveredBits(), styles.auditText());
        setText(row, 42, frame.recoveredByteString(), styles.auditText());
    }

    private String protocolByte(FrameResult frame, int coreByteIndex) {
        if (!frame.headerLocated() || coreByteIndex >= frame.recoveredBytes().size()) {
            return "未恢复";
        }
        return "0x" + FrameResult.hex(frame.recoveredBytes().get(coreByteIndex));
    }

    private void writeSummarySheet(Workbook workbook, ParseOutcome outcome, Styles styles) {
        Sheet sheet = workbook.createSheet("处理摘要");
        int rowIndex = 0;
        rowIndex = summaryRow(sheet, rowIndex, "输入文件", outcome.inputDisplayName(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "读取边沿数量", outcome.edgeCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "候选区段数量", outcome.segmentCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "有效帧数量", outcome.validFrameCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "非有效帧数量", outcome.invalidFrameCount(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "未识别/边界诊断数量", outcome.boundaryFragments().size(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "遥控器 ID", hexList(outcome.remoteId()), styles);
        rowIndex = summaryRow(sheet, rowIndex, "合法单bit脉宽窗口(us)",
                LocalPeriodEstimator.MIN_SINGLE_BIT_PULSE_US.toPlainString()
                        + "～" + LocalPeriodEstimator.MAX_SINGLE_BIT_PULSE_US.toPlainString(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "周期估计方式",
                "同步AA连续边沿初步拟合，确认AA 2D D4后按已知跳变位置细化拟合", styles);
        rowIndex = summaryRow(sheet, rowIndex, "采样相位步长", "T_est / 16", styles);
        rowIndex = summaryRow(sheet, rowIndex, "同一物理帧去重容差", "两个候选中较大T_est的1/2", styles);
        rowIndex = summaryRow(sheet, rowIndex, "空闲阈值[ms]", outcome.idleThresholdMs(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "空闲电平", outcome.idleLevel(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "电平映射", outcome.levelMapping().description(), styles);
        rowIndex = summaryRow(sheet, rowIndex, "处理时间", outcome.processedAt().format(TIME_FORMATTER), styles);

        Row fragmentTitle = sheet.createRow(rowIndex++);
        setText(fragmentTitle, 0, "未识别与边界诊断", styles.groupHeader());
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

    private void setDecimalOrText(
            Row row,
            int column,
            BigDecimal value,
            String emptyText,
            CellStyle decimalStyle,
            CellStyle textStyle) {
        if (value == null) {
            setText(row, column, emptyText, textStyle);
        } else {
            setDecimal(row, column, value, decimalStyle);
        }
    }

    private void setIntegerOrText(Row row, int column, int value, Styles styles) {
        if (value < 0) {
            setText(row, column, "不适用", styles.data());
        } else {
            setNumber(row, column, value, styles.data());
        }
    }

    private void setResultColumnWidths(Sheet sheet) {
        for (int column = 0; column < AUDIT_START_COLUMN; column++) {
            sheet.setColumnWidth(column, column >= 14 ? 16 * 256 : 12 * 256);
        }
        for (int column = AUDIT_START_COLUMN; column <= AUDIT_END_COLUMN; column++) {
            int width = switch (column) {
                case 22, 24, 40, 41, 42 -> 48;
                case 17, 18, 19, 20 -> 28;
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
        detailHeader.setWrapText(true);
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
