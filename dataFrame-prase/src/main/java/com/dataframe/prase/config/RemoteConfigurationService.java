package com.dataframe.prase.config;

import com.dataframe.prase.enums.DfpErrorCode;
import com.dataframe.prase.signal.LevelMapping;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RemoteConfigurationService {

    private final Path configFile;
    private final ObjectMapper objectMapper;

    @Autowired
    public RemoteConfigurationService(ObjectMapper objectMapper) {
        this(Path.of("config", "remote-configs.json"), objectMapper);
    }

    public RemoteConfigurationService(Path configFile, ObjectMapper objectMapper) {
        this.configFile = configFile;
        this.objectMapper = objectMapper;
    }

    public List<RemoteConfigSummary> listSummaries() {
        return loadAll().stream()
                .map(configuration -> new RemoteConfigSummary(configuration.key(), configuration.name()))
                .toList();
    }

    public RemoteConfiguration getRequired(String key) {
        if (key == null || key.isBlank()) {
            throw configNotFound(key);
        }
        return loadAll().stream()
                .filter(configuration -> configuration.key().equals(key))
                .findFirst()
                .orElseThrow(() -> configNotFound(key));
    }

    private List<RemoteConfiguration> loadAll() {
        ConfigDocument document;
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            document = objectMapper.readValue(reader, ConfigDocument.class);
        } catch (JsonProcessingException exception) {
            throw new RemoteConfigException(
                    DfpErrorCode.CONFIG_INVALID,
                    "配置文件 JSON 格式无效: " + configFile,
                    exception);
        } catch (IOException exception) {
            throw new RemoteConfigException(
                    DfpErrorCode.CONFIG_READ_FAILED,
                    "无法读取配置文件: " + configFile,
                    exception);
        }

        if (document == null || document.configs() == null || document.configs().isEmpty()) {
            throw invalid("至少一套遥控器配置是必需的（configs）");
        }

        List<RemoteConfiguration> configurations = new ArrayList<>(document.configs().size());
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < document.configs().size(); index++) {
            RawRemoteConfiguration raw = document.configs().get(index);
            if (raw == null) {
                throw invalid("configs[" + index + "] 不能为空");
            }
            RemoteConfiguration configuration = validate(raw, index);
            if (!keys.add(configuration.key())) {
                throw invalid("key 不能重复: " + configuration.key());
            }
            configurations.add(configuration);
        }
        return List.copyOf(configurations);
    }

    private RemoteConfiguration validate(RawRemoteConfiguration raw, int index) {
        String prefix = "configs[" + index + "].";
        String key = requireText(raw.key(), prefix + "key");
        String name = requireText(raw.name(), prefix + "name");
        List<Integer> remoteId = parseRemoteId(raw.remoteId(), prefix + "remoteId");
        Charset charset = parseCharset(raw.charset(), prefix + "charset");

        BigDecimal idleThresholdMs = raw.idleThresholdMs();
        if (idleThresholdMs == null || idleThresholdMs.signum() <= 0) {
            throw invalid(prefix + "idleThresholdMs 必须大于 0");
        }
        Integer idleLevel = raw.idleLevel();
        if (idleLevel == null || (idleLevel != 0 && idleLevel != 1)) {
            throw invalid(prefix + "idleLevel 只能是 0 或 1");
        }
        String levelMappingText = requireText(raw.levelMapping(), prefix + "levelMapping");
        LevelMapping levelMapping;
        try {
            levelMapping = LevelMapping.parse(levelMappingText);
        } catch (IllegalArgumentException exception) {
            throw invalid(prefix + "levelMapping 只能是 direct 或 inverted");
        }

        return new RemoteConfiguration(
                key,
                name,
                remoteId,
                charset,
                idleThresholdMs,
                idleLevel,
                levelMapping);
    }

    private List<Integer> parseRemoteId(String value, String fieldName) {
        String text = requireText(value, fieldName);
        String[] parts = text.split("\\s+");
        if (parts.length != 3) {
            throw invalid(fieldName + " 必须是 3 个十六进制字节，例如 B0 42 87");
        }
        List<Integer> bytes = new ArrayList<>(3);
        for (String part : parts) {
            if (!part.matches("[0-9A-Fa-f]{2}")) {
                throw invalid(fieldName + " 必须是 3 个十六进制字节，例如 B0 42 87");
            }
            bytes.add(Integer.parseInt(part, 16));
        }
        return List.copyOf(bytes);
    }

    private Charset parseCharset(String value, String fieldName) {
        String text = requireText(value, fieldName);
        try {
            return Charset.forName(text);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            throw invalid(fieldName + " 不是当前 JDK 支持的字符集: " + text);
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private RemoteConfigException invalid(String message) {
        return new RemoteConfigException(DfpErrorCode.CONFIG_INVALID, message);
    }

    private RemoteConfigException configNotFound(String key) {
        return new RemoteConfigException(
                DfpErrorCode.CONFIG_NOT_FOUND,
                "遥控器配置不存在，请刷新配置列表后重试: " + key);
    }


    private record ConfigDocument(List<RawRemoteConfiguration> configs) {
    }


    private record RawRemoteConfiguration(
            String key,
            String name,
            String remoteId,
            String charset,
            BigDecimal idleThresholdMs,
            Integer idleLevel,
            String levelMapping) {
    }
}
