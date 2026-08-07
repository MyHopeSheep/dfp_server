package com.dataframe.prase.infrastructure.configuration;

import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.dto.ParserConfigurationDocument;
import com.dataframe.prase.domain.exception.ParserConfigurationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ParserConfigurationFileReader {

    private final Path configFile;
    private final ObjectMapper objectMapper;

    public ParserConfigurationFileReader(Path configFile, ObjectMapper objectMapper) {
        this.configFile = configFile;
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ParserConfigurationDocument read() {
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            return objectMapper.readValue(reader, ParserConfigurationDocument.class);
        } catch (JsonProcessingException exception) {
            throw new ParserConfigurationException(
                    ParserErrorCode.CONFIG_INVALID,
                    "配置文件 JSON 格式无效: " + configFile + ": " + exception.getOriginalMessage(),
                    exception);
        } catch (IOException exception) {
            throw new ParserConfigurationException(
                    ParserErrorCode.CONFIG_READ_FAILED,
                    "无法读取配置文件: " + configFile,
                    exception);
        }
    }
}
