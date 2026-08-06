package com.dataframe.prase.protocol;

import com.dataframe.prase.model.ActivitySegment;
import com.dataframe.prase.model.BoundaryFragment;
import com.dataframe.prase.model.EdgeRecord;
import com.dataframe.prase.model.FrameAudit;
import com.dataframe.prase.model.FrameResult;
import com.dataframe.prase.signal.EstimatedFrameSampler;
import com.dataframe.prase.signal.FixedPeriodSampler;
import com.dataframe.prase.signal.FrameTimingRules;
import com.dataframe.prase.signal.LevelMapping;
import com.dataframe.prase.signal.LocalPeriodEstimator;
import com.dataframe.prase.signal.PeriodFit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FrameDecoder {

    private static final int LEGACY_SYNC_INCLUSIVE_BITS = 112;
    private static final int FORMAL_FRAME_BITS = 104;
    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final List<Integer> MARKER_BITS = List.of(
            1, 0, 1, 0, 1, 0, 1, 0,
            0, 0, 1, 0, 1, 1, 0, 1,
            1, 1, 0, 1, 0, 1, 0, 0);
    private static final List<Integer> MARKER_TRANSITION_INDEXES = List.of(
            0, 1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 14, 15, 18, 19, 20, 21, 22);

    private final FrameRules frameRules;
    private final LocalPeriodEstimator localPeriodEstimator = new LocalPeriodEstimator();
    private final EstimatedFrameSampler estimatedFrameSampler = new EstimatedFrameSampler();

    public FrameDecoder(FrameRules frameRules) {
        this.frameRules = Objects.requireNonNull(frameRules, "frameRules");
    }

    public List<FrameResult> decodeEstimated(
            List<EdgeRecord> edges,
            List<Integer> expectedRemoteId,
            LevelMapping levelMapping) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(expectedRemoteId, "expectedRemoteId");
        Objects.requireNonNull(levelMapping, "levelMapping");
        if (edges.size() < LocalPeriodEstimator.MIN_VALID_PULSE_COUNT + 1) {
            return List.of();
        }

        List<FrameResult> results = new ArrayList<>();
        for (LocalPeriodEstimator.InitialCandidate initial
                : localPeriodEstimator.findInitialCandidates(edges)) {
            BigDecimal initialSyncStart = predictedTimeSeconds(initial.fit(), 0);
            boolean initialMarkerConfirmed = estimatedFrameSampler
                    .phaseCandidates(initial.fit().periodUs()).stream()
                    .map(phase -> estimatedFrameSampler.sample(
                            edges,
                            initialSyncStart,
                            initial.fit().periodUs(),
                            phase,
                            MARKER_BITS.size(),
                            levelMapping))
                    .anyMatch(sampled -> sampled.bits().equals(MARKER_BITS));
            if (!initialMarkerConfirmed) {
                continue;
            }

            Optional<PeriodFit> refinedOptional = refineMarkerFit(edges, initial);
            if (refinedOptional.isEmpty()) {
                continue;
            }
            PeriodFit refined = refinedOptional.get();
            if (refined.residualRatio().compareTo(LocalPeriodEstimator.MAX_FIT_RESIDUAL_RATIO) > 0) {
                continue;
            }
            BigDecimal refinedSyncStart = predictedTimeSeconds(refined, 0);
            for (BigDecimal phaseUs : estimatedFrameSampler.phaseCandidates(refined.periodUs())) {
                EstimatedFrameSampler.SampledWindow syncSample = estimatedFrameSampler.sample(
                        edges,
                        refinedSyncStart,
                        refined.periodUs(),
                        phaseUs,
                        MARKER_BITS.size(),
                        levelMapping);
                if (!syncSample.bits().equals(MARKER_BITS)) {
                    continue;
                }
                results.add(recoverFormalFrame(
                        edges,
                        initial,
                        refined,
                        refinedSyncStart,
                        phaseUs,
                        expectedRemoteId,
                        levelMapping));
            }
        }
        return List.copyOf(results);
    }

    public List<FrameResult> decode(
            ActivitySegment segment,
            FixedPeriodSampler.SampledBits sampled,
            List<Integer> expectedRemoteId) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(sampled, "sampled");
        List<FrameResult> results = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom <= sampled.bits().size() - MARKER_BITS.size()) {
            int startBit = findMarker(sampled.bits(), searchFrom);
            if (startBit < 0) {
                break;
            }
            int availableBits = Math.min(LEGACY_SYNC_INCLUSIVE_BITS, sampled.bits().size() - startBit);
            List<Integer> coreBits = List.copyOf(sampled.bits().subList(startBit, startBit + availableBits));
            List<Integer> recoveredBytes = toBytes(coreBits);
            FrameRules.ValidationResult validation = frameRules.validate(recoveredBytes, expectedRemoteId);
            BigDecimal startSeconds = sampled.sampleTimesSeconds().get(startBit);
            BigDecimal endSeconds = frameEnd(
                    startSeconds, availableBits, sampled.bitPeriodUs(), segment.endSeconds());

            results.add(new FrameResult(
                    0,
                    segment.number(),
                    true,
                    validation.complete(),
                    validation.valid(),
                    startSeconds,
                    endSeconds,
                    sampled.bitPeriodUs(),
                    sampled.phaseUs(),
                    startBit,
                    toBitString(coreBits),
                    recoveredBytes,
                    validation.failureReasons(),
                    validation.commandInfo().channel(),
                    validation.commandInfo().action()));

            searchFrom = validation.complete()
                    ? startBit + LEGACY_SYNC_INCLUSIVE_BITS
                    : sampled.bits().size();
        }
        return List.copyOf(results);
    }

    public FrameResult unmatched(ActivitySegment segment, FixedPeriodSampler.SampledBits sampled) {
        List<Integer> bytes = toBytes(sampled.bits());
        BigDecimal start = sampled.sampleTimesSeconds().isEmpty()
                ? segment.startSeconds()
                : sampled.sampleTimesSeconds().get(0);
        return new FrameResult(
                0,
                segment.number(),
                false,
                false,
                false,
                start,
                segment.endSeconds(),
                sampled.bitPeriodUs(),
                sampled.phaseUs(),
                -1,
                sampled.bitString(),
                bytes,
                List.of("未找到识别开头"),
                "未知通道",
                "未知命令");
    }

    public List<BoundaryFragment> boundaryFragments(
            ActivitySegment segment,
            List<FrameResult> frames) {
        if (!segment.isFileBoundarySegment()) {
            return List.of();
        }
        List<FrameResult> ordered = frames.stream()
                .sorted(Comparator.comparing(FrameResult::startSeconds))
                .toList();
        if (ordered.isEmpty()) {
            return List.of(new BoundaryFragment(
                    segment.startSeconds(),
                    segment.endSeconds(),
                    "文件边界缺少完整活动边界且未找到识别开头"));
        }

        List<BoundaryFragment> fragments = new ArrayList<>();
        FrameResult first = ordered.get(0);
        BigDecimal firstCoverageStart = first.audit().preludeStartSeconds() == null
                ? first.startSeconds()
                : first.audit().preludeStartSeconds();
        if (!segment.leftBoundaryKnown()
                && segment.startSeconds().compareTo(firstCoverageStart) < 0) {
            fragments.add(new BoundaryFragment(
                    segment.startSeconds(), firstCoverageStart, "文件开头残片"));
        }
        FrameResult last = ordered.get(ordered.size() - 1);
        if (!segment.rightBoundaryKnown()
                && last.endSeconds().compareTo(segment.endSeconds()) < 0) {
            fragments.add(new BoundaryFragment(
                    last.endSeconds(), segment.endSeconds(), "文件结尾残片"));
        }
        return List.copyOf(fragments);
    }

    private Optional<PeriodFit> refineMarkerFit(
            List<EdgeRecord> edges,
            LocalPeriodEstimator.InitialCandidate initial) {
        BigDecimal initialSyncStart = predictedTimeSeconds(initial.fit(), 0);
        BigDecimal halfPeriodSeconds = initial.fit().periodUs()
                .multiply(new BigDecimal("0.5"), MATH_CONTEXT)
                .divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        List<EdgeRecord> markerEdges = new ArrayList<>(MARKER_TRANSITION_INDEXES.size());
        List<Integer> bitIndexes = new ArrayList<>(MARKER_TRANSITION_INDEXES.size());
        int previousEdgeIndex = initial.firstEdgeIndex() - 1;

        for (int bitIndex : MARKER_TRANSITION_INDEXES) {
            BigDecimal expectedTime = initialSyncStart.add(
                    initial.fit().periodUs()
                            .multiply(BigDecimal.valueOf(bitIndex), MATH_CONTEXT)
                            .divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT),
                    MATH_CONTEXT);
            int mappedIndex = nearestEdgeIndex(
                    edges, previousEdgeIndex + 1, expectedTime, halfPeriodSeconds);
            if (mappedIndex < 0) {
                return Optional.empty();
            }
            markerEdges.add(edges.get(mappedIndex));
            bitIndexes.add(bitIndex);
            previousEdgeIndex = mappedIndex;
        }
        return localPeriodEstimator.fit(markerEdges, bitIndexes);
    }

    private int nearestEdgeIndex(
            List<EdgeRecord> edges,
            int fromIndex,
            BigDecimal expectedTime,
            BigDecimal toleranceSeconds) {
        BigDecimal lower = expectedTime.subtract(toleranceSeconds);
        BigDecimal upper = expectedTime.add(toleranceSeconds);
        int bestIndex = -1;
        BigDecimal bestDistance = null;
        for (int index = Math.max(0, fromIndex); index < edges.size(); index++) {
            BigDecimal edgeTime = edges.get(index).timeSeconds();
            if (edgeTime.compareTo(lower) < 0) {
                continue;
            }
            if (edgeTime.compareTo(upper) > 0) {
                break;
            }
            BigDecimal distance = edgeTime.subtract(expectedTime).abs();
            if (bestDistance == null || distance.compareTo(bestDistance) < 0) {
                bestIndex = index;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }

    private FrameResult recoverFormalFrame(
            List<EdgeRecord> edges,
            LocalPeriodEstimator.InitialCandidate initial,
            PeriodFit refined,
            BigDecimal syncStart,
            BigDecimal phaseUs,
            List<Integer> expectedRemoteId,
            LevelMapping levelMapping) {
        BigDecimal periodSeconds = refined.periodUs().divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal formalStart = syncStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(8), MATH_CONTEXT), MATH_CONTEXT);
        BigDecimal formalEnd = formalStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(FORMAL_FRAME_BITS), MATH_CONTEXT), MATH_CONTEXT);
        EstimatedFrameSampler.SampledWindow direct = estimatedFrameSampler.sample(
                edges,
                formalStart,
                refined.periodUs(),
                phaseUs,
                FORMAL_FRAME_BITS,
                levelMapping);

        TailRecovery tail = recoverTailIfAllowed(
                edges, direct, formalStart, formalEnd, refined.periodUs(), phaseUs, levelMapping);
        List<Integer> finalBits = tail.finalBits();
        List<Integer> recoveredBytes = toBytes(finalBits);
        FrameRules.ValidationResult validation = frameRules.validate(recoveredBytes, expectedRemoteId);
        BigDecimal phaseStepUs = refined.periodUs()
                .divide(BigDecimal.valueOf(EstimatedFrameSampler.PHASE_COUNT), MATH_CONTEXT);
        int lastAssociatedIndex = strictPreviousEdgeIndex(edges, formalEnd);
        BigDecimal lastAssociatedTime = lastAssociatedIndex < 0
                ? edges.get(initial.firstEdgeIndex()).timeSeconds()
                : edges.get(lastAssociatedIndex).timeSeconds();
        BigDecimal safetyRatio = direct.minimumSafetyDistanceRatio();
        if (tail.extended() && tail.syntheticSafetyRatio() != null
                && tail.syntheticSafetyRatio().compareTo(safetyRatio) < 0) {
            safetyRatio = tail.syntheticSafetyRatio();
        }

        FrameAudit audit = new FrameAudit(
                edges.get(initial.firstEdgeIndex()).timeSeconds(),
                lastAssociatedTime,
                formalStart,
                formalEnd,
                "同步AA连续1T边沿初始拟合，确认AA 2D D4后按已知跳变位置细化拟合并重新采样验证",
                initial.validSingleBitPulseCount(),
                initial.fit().periodUs(),
                initial.fit().maxResidualUs(),
                initial.fit().residualRatio(),
                initial.fit().periodUs().multiply(LocalPeriodEstimator.MAX_FIT_RESIDUAL_RATIO),
                refined.maxResidualUs(),
                refined.residualRatio(),
                refined.periodUs().multiply(LocalPeriodEstimator.MAX_FIT_RESIDUAL_RATIO),
                phaseUs,
                phaseStepUs,
                direct.directBitCount(),
                finalBits.size(),
                tail.tailEdgeDeviationUs(),
                tail.tailEdgeToleranceUs(),
                tail.previousPulseErrorUs(),
                tail.status(),
                safetyRatio,
                tail.extended());
        return new FrameResult(
                0,
                1,
                true,
                validation.complete(),
                validation.valid(),
                formalStart,
                formalEnd,
                refined.periodUs(),
                phaseUs,
                0,
                toBitString(finalBits),
                recoveredBytes,
                validation.failureReasons(),
                validation.commandInfo().channel(),
                validation.commandInfo().action(),
                audit);
    }

    private TailRecovery recoverTailIfAllowed(
            List<EdgeRecord> edges,
            EstimatedFrameSampler.SampledWindow direct,
            BigDecimal formalStart,
            BigDecimal formalEnd,
            BigDecimal periodUs,
            BigDecimal phaseUs,
            LevelMapping levelMapping) {
        List<Integer> finalBits = new ArrayList<>(direct.bits());
        if (direct.directBitCount() == FORMAL_FRAME_BITS) {
            return new TailRecovery(
                    finalBits, false, "不适用，CSV已直接恢复104 bit",
                    null, null, null, null);
        }
        if (direct.directBitCount() != FORMAL_FRAME_BITS - 1 || edges.size() < 2) {
            return new TailRecovery(
                    finalBits, false, "恢复失败，直接恢复不足103 bit，禁止补齐",
                    null, null, null, null);
        }

        EdgeRecord previous = edges.get(edges.size() - 2);
        EdgeRecord last = edges.get(edges.size() - 1);
        BigDecimal periodSeconds = periodUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal lastBitStart = formalStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(103), MATH_CONTEXT), MATH_CONTEXT);
        BigDecimal tailEdgeDeviationUs = last.timeSeconds().subtract(lastBitStart).abs()
                .multiply(MICROSECONDS_PER_SECOND);
        BigDecimal tailToleranceUs = periodUs.multiply(FrameTimingRules.TAIL_EDGE_TOLERANCE_RATIO);
        BigDecimal previousPulseUs = last.timeSeconds().subtract(previous.timeSeconds())
                .multiply(MICROSECONDS_PER_SECOND);
        BigDecimal previousPulseErrorUs = previousPulseUs.subtract(periodUs).abs();
        BigDecimal lastSampleTime = formalStart
                .add(phaseUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT), MATH_CONTEXT)
                .add(periodSeconds.multiply(BigDecimal.valueOf(103), MATH_CONTEXT), MATH_CONTEXT);

        boolean credible = previous.timeSeconds().compareTo(last.timeSeconds()) < 0
                && previous.level() != last.level()
                && tailEdgeDeviationUs.compareTo(tailToleranceUs) <= 0
                && LocalPeriodEstimator.isSingleBitPulseUs(previousPulseUs)
                && previousPulseErrorUs.compareTo(
                        periodUs.multiply(FrameTimingRules.PREVIOUS_PULSE_TOLERANCE_RATIO)) <= 0
                && last.timeSeconds().compareTo(lastSampleTime) <= 0
                && lastSampleTime.compareTo(formalEnd) < 0;
        if (!credible) {
            String status = last.timeSeconds().compareTo(lastBitStart.subtract(
                    tailToleranceUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT))) < 0
                    ? "恢复失败，未采集第104 bit起始边沿"
                    : "恢复失败，最后边沿不符合第104 bit预期起点及局部周期约束";
            return new TailRecovery(
                    finalBits, false, status,
                    tailEdgeDeviationUs, tailToleranceUs, previousPulseErrorUs, null);
        }

        finalBits.add(levelMapping.toBit(last.level()));
        BigDecimal syntheticSafetyRatio = lastSampleTime.subtract(last.timeSeconds())
                .divide(periodSeconds, MATH_CONTEXT);
        return new TailRecovery(
                finalBits,
                true,
                "恢复成功，最后1 bit电平已记录，仅缺结束边界，延伸最后已知电平到正式帧估算结束时间",
                tailEdgeDeviationUs,
                tailToleranceUs,
                previousPulseErrorUs,
                syntheticSafetyRatio);
    }

    private BigDecimal predictedTimeSeconds(PeriodFit fit, int originalBitIndex) {
        return fit.originSeconds().add(
                fit.predictedEdgeTimeUs(originalBitIndex)
                        .divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT),
                MATH_CONTEXT);
    }

    private int strictPreviousEdgeIndex(List<EdgeRecord> edges, BigDecimal timeSeconds) {
        int index = estimatedFrameSampler.previousEdgeIndex(edges, timeSeconds);
        while (index >= 0 && edges.get(index).timeSeconds().compareTo(timeSeconds) >= 0) {
            index--;
        }
        return index;
    }

    private int findMarker(List<Integer> bits, int fromIndex) {
        for (int index = fromIndex; index <= bits.size() - MARKER_BITS.size(); index++) {
            boolean matches = true;
            for (int markerIndex = 0; markerIndex < MARKER_BITS.size(); markerIndex++) {
                if (!bits.get(index + markerIndex).equals(MARKER_BITS.get(markerIndex))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }

    private List<Integer> toBytes(List<Integer> bits) {
        List<Integer> bytes = new ArrayList<>();
        for (int offset = 0; offset + 7 < bits.size(); offset += 8) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) {
                value = (value << 1) | bits.get(offset + bit);
            }
            bytes.add(value);
        }
        return List.copyOf(bytes);
    }

    private String toBitString(List<Integer> bits) {
        StringBuilder builder = new StringBuilder(bits.size());
        for (int bit : bits) {
            builder.append(bit);
        }
        return builder.toString();
    }

    private BigDecimal frameEnd(
            BigDecimal startSeconds,
            int bitCount,
            BigDecimal bitPeriodUs,
            BigDecimal segmentEnd) {
        BigDecimal duration = bitPeriodUs
                .multiply(BigDecimal.valueOf(bitCount))
                .divide(MICROSECONDS_PER_SECOND);
        BigDecimal calculatedEnd = startSeconds.add(duration);
        return calculatedEnd.min(segmentEnd);
    }

    private record TailRecovery(
            List<Integer> finalBits,
            boolean extended,
            String status,
            BigDecimal tailEdgeDeviationUs,
            BigDecimal tailEdgeToleranceUs,
            BigDecimal previousPulseErrorUs,
            BigDecimal syntheticSafetyRatio) {
        private TailRecovery {
            finalBits = List.copyOf(finalBits);
        }
    }
}
