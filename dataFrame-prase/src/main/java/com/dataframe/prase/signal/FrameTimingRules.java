package com.dataframe.prase.signal;

import java.math.BigDecimal;

public final class FrameTimingRules {

    public static final BigDecimal PREFILTER_NOMINAL_US = new BigDecimal("416");
    public static final BigDecimal PREFILTER_TOLERANCE_RATIO = new BigDecimal("0.20");
    public static final BigDecimal MIN_SINGLE_BIT_PULSE_US = new BigDecimal("332.8");
    public static final BigDecimal MAX_SINGLE_BIT_PULSE_US = new BigDecimal("499.2");

    public static final int MIN_VALID_PULSE_COUNT = 5;
    public static final int MAX_INITIAL_FIT_PULSE_COUNT = 16;
    public static final BigDecimal MAX_FIT_RESIDUAL_RATIO = new BigDecimal("0.15");
    public static final BigDecimal TAIL_EDGE_TOLERANCE_RATIO = new BigDecimal("0.35");

    public static final int PHASE_COUNT = 16;
    public static final BigDecimal DEDUP_TOLERANCE_RATIO = new BigDecimal("0.5");
    public static final BigDecimal PREVIOUS_PULSE_TOLERANCE_RATIO =
            MAX_FIT_RESIDUAL_RATIO.multiply(BigDecimal.valueOf(2));

    private FrameTimingRules() {
    }
}
