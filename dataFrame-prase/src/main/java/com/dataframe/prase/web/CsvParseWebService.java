package com.dataframe.prase.web;

import com.dataframe.prase.config.RemoteConfiguration;
import com.dataframe.prase.config.RemoteConfigurationService;
import com.dataframe.prase.enums.DfpErrorCode;
import com.dataframe.prase.exception.DfpException;
import com.dataframe.prase.service.ParseOptions;
import com.dataframe.prase.service.ParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@Slf4j
public class CsvParseWebService {

    private final RemoteConfigurationService configurationService;
    private final ParseService parseService;
    private final Path tempRoot;

    @Autowired
    public CsvParseWebService(
            RemoteConfigurationService configurationService,
            ParseService parseService) {
        this(configurationService, parseService, Path.of(System.getProperty("java.io.tmpdir")));
    }

    CsvParseWebService(
            RemoteConfigurationService configurationService,
            ParseService parseService,
            Path tempRoot) {
        this.configurationService = configurationService;
        this.parseService = parseService;
        this.tempRoot = tempRoot;
    }

    public ParseDownload parse(MultipartFile file, String configKey) throws IOException {
        RemoteConfiguration configuration = configurationService.getRequired(configKey);
        String originalFileName = validateAndNormalizeFileName(file);
        long startNanos = System.nanoTime();
        Path requestDirectory = null;
        Path input = null;
        Path output = null;
        try {
            requestDirectory = Files.createTempDirectory(tempRoot, "dfp-");
            input = requestDirectory.resolve("input.csv");
            output = requestDirectory.resolve("result.xlsx");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, input, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new DfpException(DfpErrorCode.CSV_READ_FAILED, "上传 CSV 无法读取", exception);
            }

            parseService.parse(new ParseOptions(
                    input,
                    output,
                    configuration.remoteId(),
                    configuration.charset(),
                    configuration.idleThresholdMs(),
                    configuration.idleLevel(),
                    configuration.levelMapping(),
                    originalFileName));
            byte[] content;
            try {
                content = Files.readAllBytes(output);
            } catch (IOException exception) {
                throw new DfpException(DfpErrorCode.EXCEL_GENERATION_FAILED, "Excel 生成失败", exception);
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info(
                    "CSV 解析成功: fileName={}, configKey={}, elapsedMs={}",
                    originalFileName,
                    configKey,
                    elapsedMs);
            return new ParseDownload(downloadFileName(originalFileName), content);
        } catch (IOException | RuntimeException exception) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.warn(
                    "CSV 解析失败: fileName={}, configKey={}, elapsedMs={}, failureType={}",
                    originalFileName,
                    configKey,
                    elapsedMs,
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            deleteKnownTemporaryFiles(input, output, requestDirectory);
        }
    }

    private String validateAndNormalizeFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的 CSV 文件不能为空");
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        int separatorIndex = Math.max(
                originalFileName.lastIndexOf('/'),
                originalFileName.lastIndexOf('\\'));
        String safeFileName = originalFileName.substring(separatorIndex + 1).trim();
        if (safeFileName.length() <= 4
                || !safeFileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("只支持上传 .csv 文件");
        }
        return safeFileName;
    }

    private String downloadFileName(String csvFileName) {
        return csvFileName.substring(0, csvFileName.length() - 4) + "-解析结果.xlsx";
    }

    private void deleteKnownTemporaryFiles(Path input, Path output, Path requestDirectory) {
        deleteIfExists(output);
        deleteIfExists(input);
        deleteIfExists(requestDirectory);
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("临时文件清理失败: path={}", path.getFileName());
        }
    }
}
