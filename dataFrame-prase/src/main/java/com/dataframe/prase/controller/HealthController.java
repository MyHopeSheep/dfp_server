package com.dataframe.prase.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/dfp")
public class HealthController {

    private final Environment environment;
    private final String version;

    public HealthController(
            Environment environment,
            @Value("${spring.application.version:1.0.0-SNAPSHOT}") String version) {
        this.environment = environment;
        this.version = version;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port == null) {
            port = environment.getProperty("server.port", Integer.class, 8080);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("port", port);
        response.put("version", version);
        return response;
    }
}
