package com.dataframe.prase.controller;

import com.dataframe.prase.domain.vo.ParserSettingsView;
import com.dataframe.prase.service.ParserConfigurationService;
import com.dataframe.prase.domain.vo.RemoteProfileView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dfp/config")
public class ParserConfigurationController {

    private final ParserConfigurationService configurationService;

    public ParserConfigurationController(ParserConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/remoteProfiles")
    public List<RemoteProfileView> listRemoteProfiles() {
        return configurationService.listRemoteProfiles();
    }

    @GetMapping("/parserSettings")
    public ParserSettingsView getParserSettings() {
        return configurationService.getParserSettings();
    }
}
