package com.dataframe.prase.web;

import com.dataframe.prase.config.RemoteConfigSummary;
import com.dataframe.prase.config.RemoteConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dfp")
public class RemoteConfigController {

    private final RemoteConfigurationService configurationService;

    public RemoteConfigController(RemoteConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping("/configs")
    public List<RemoteConfigSummary> listConfigurations() {
        return configurationService.listSummaries();
    }
}
