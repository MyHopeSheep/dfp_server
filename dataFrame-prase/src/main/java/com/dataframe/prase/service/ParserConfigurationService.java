package com.dataframe.prase.service;

import com.dataframe.prase.common.ParserErrorCode;
import com.dataframe.prase.domain.configuration.ParserConfigurationValidator;
import com.dataframe.prase.domain.model.ParserConfigurationSnapshot;
import com.dataframe.prase.domain.model.RemoteProfile;
import com.dataframe.prase.domain.model.ResolvedParserConfiguration;
import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import com.dataframe.prase.domain.vo.ParserSettingsView;
import com.dataframe.prase.domain.vo.RemoteProfileView;
import com.dataframe.prase.domain.exception.ParserConfigurationException;
import com.dataframe.prase.infrastructure.configuration.ParserConfigurationFileReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ParserConfigurationService {

    private final ParserConfigurationFileReader fileReader;
    private final ParserConfigurationValidator validator;
    private final ParserConfigurationViewMapper viewMapper;

    @Autowired
    public ParserConfigurationService(ObjectMapper objectMapper) {
        this(
                new ParserConfigurationFileReader(
                        Path.of("config", "parser-config.json"), objectMapper),
                new ParserConfigurationValidator(),
                new ParserConfigurationViewMapper());
    }

    public ParserConfigurationService(Path configFile, ObjectMapper objectMapper) {
        this(
                new ParserConfigurationFileReader(configFile, objectMapper),
                new ParserConfigurationValidator(),
                new ParserConfigurationViewMapper());
    }

    ParserConfigurationService(
            ParserConfigurationFileReader fileReader,
            ParserConfigurationValidator validator,
            ParserConfigurationViewMapper viewMapper) {
        this.fileReader = fileReader;
        this.validator = validator;
        this.viewMapper = viewMapper;
    }

    public ParserConfigurationSnapshot loadSnapshot() {
        return validator.validate(fileReader.read());
    }

    public ResolvedParserConfiguration resolveRequired(String key) {
        if (key == null || key.isBlank()) {
            throw configNotFound(key);
        }
        ParserConfigurationSnapshot snapshot = loadSnapshot();
        RemoteProfile remote = snapshot.remoteConfigurations().stream()
                .filter(configuration -> configuration.key().equals(key.trim()))
                .findFirst()
                .orElseThrow(() -> configNotFound(key));
        ProtocolDecodingRules rule = snapshot.protocolRuleSets().stream()
                .filter(candidate -> candidate.key().equals(remote.protocolRuleKey()))
                .findFirst()
                .orElseThrow(() -> invalid("remoteConfigs.protocolRuleKey 关联规则不存在"));
        return new ResolvedParserConfiguration(remote, rule);
    }

    public List<RemoteProfileView> listRemoteProfiles() {
        return viewMapper.toRemoteProfileViews(loadSnapshot());
    }

    public ParserSettingsView getParserSettings() {
        return viewMapper.toSettingsView(loadSnapshot());
    }

    private ParserConfigurationException invalid(String message) {
        return new ParserConfigurationException(ParserErrorCode.CONFIG_INVALID, message);
    }

    private ParserConfigurationException configNotFound(String key) {
        return new ParserConfigurationException(
                ParserErrorCode.CONFIG_NOT_FOUND,
                "遥控器配置不存在，请刷新配置列表后重试: " + key);
    }
}
