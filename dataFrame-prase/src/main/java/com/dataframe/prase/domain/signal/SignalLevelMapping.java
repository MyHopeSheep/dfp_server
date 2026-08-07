package com.dataframe.prase.domain.signal;

import java.util.Locale;

public enum SignalLevelMapping {

    DIRECT("direct", "直映射（CSV 0 -> 0，CSV 1 -> 1）"),
    INVERTED("inverted", "反相映射（CSV 0 -> 1，CSV 1 -> 0）");

    private final String cliValue;
    private final String description;

    SignalLevelMapping(String cliValue, String description) {
        this.cliValue = cliValue;
        this.description = description;
    }

    public static SignalLevelMapping parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (SignalLevelMapping mapping : values()) {
            if (mapping.cliValue.equals(normalized)) {
                return mapping;
            }
        }
        throw new IllegalArgumentException("--level-mapping 只能是 direct 或 inverted: " + value);
    }

    public int toBit(int csvLevel) {
        if (csvLevel != 0 && csvLevel != 1) {
            throw new IllegalArgumentException("CSV 电平只能是 0 或 1");
        }
        return this == DIRECT ? csvLevel : 1 - csvLevel;
    }

    public String cliValue() {
        return cliValue;
    }

    public String description() {
        return description;
    }
}
