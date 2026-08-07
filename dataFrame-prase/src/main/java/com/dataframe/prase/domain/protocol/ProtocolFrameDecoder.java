package com.dataframe.prase.domain.protocol;

import com.dataframe.prase.domain.protocol.ProtocolDecodingRules;
import com.dataframe.prase.domain.model.SignalActivitySegment;
import com.dataframe.prase.domain.model.UnrecognizedSignalRange;
import com.dataframe.prase.domain.model.SignalEdge;
import com.dataframe.prase.domain.model.FrameDecodeAudit;
import com.dataframe.prase.domain.model.DecodedFrame;
import com.dataframe.prase.domain.signal.FrameWindowBitSampler;
import com.dataframe.prase.domain.signal.FixedPeriodBitSampler;
import com.dataframe.prase.domain.signal.SignalLevelMapping;
import com.dataframe.prase.domain.signal.BitPeriodEstimator;
import com.dataframe.prase.domain.signal.BitPeriodFit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ProtocolFrameDecoder {

    private static final BigDecimal MICROSECONDS_PER_SECOND = new BigDecimal("1000000");
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    private final ProtocolDecodingRules ruleSet;
    private final ProtocolFrameValidator frameRules;
    private final BitPeriodEstimator localPeriodEstimator;
    private final FrameWindowBitSampler estimatedFrameSampler;
    private final List<Integer> markerBits;
    private final List<Integer> markerTransitionIndexes;

    public ProtocolFrameDecoder(ProtocolDecodingRules ruleSet) {
        this.ruleSet = Objects.requireNonNull(ruleSet, "ruleSet");
        this.frameRules = new ProtocolFrameValidator(ruleSet);
        this.localPeriodEstimator = new BitPeriodEstimator(ruleSet);
        this.estimatedFrameSampler = new FrameWindowBitSampler(ruleSet.timing().phaseCount());
        this.markerBits = ruleSet.frame().syncMarkerBits();
        this.markerTransitionIndexes = ruleSet.derived().markerTransitionIndexes();
    }

    public List<DecodedFrame> decodeEstimated(
            List<SignalEdge> edges,
            List<Integer> expectedRemoteId,
            SignalLevelMapping levelMapping) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(expectedRemoteId, "expectedRemoteId");
        Objects.requireNonNull(levelMapping, "levelMapping");
        if (edges.size() < ruleSet.timing().minValidPulseCount() + 1) {
            return List.of();
        }

        List<DecodedFrame> results = new ArrayList<>();
        for (BitPeriodEstimator.InitialCandidate initial
                : localPeriodEstimator.findInitialCandidates(edges)) {
            BigDecimal initialSyncStart = predictedTimeSeconds(initial.fit(), 0);
            boolean initialMarkerConfirmed = estimatedFrameSampler
                    .phaseCandidates(initial.fit().periodUs()).stream()
                    .map(phase -> estimatedFrameSampler.sample(
                            edges,
                            initialSyncStart,
                            initial.fit().periodUs(),
                            phase,
                            markerBits.size(),
                            levelMapping))
                    .anyMatch(sampled -> sampled.bits().equals(markerBits));
            if (!initialMarkerConfirmed) {
                continue;
            }

            Optional<BitPeriodFit> refinedOptional = refineMarkerFit(edges, initial);
            if (refinedOptional.isEmpty()) {
                continue;
            }
            BitPeriodFit refined = refinedOptional.get();
            if (refined.residualRatio().compareTo(ruleSet.timing().maxFitResidualRatio()) > 0) {
                continue;
            }
            BigDecimal refinedSyncStart = predictedTimeSeconds(refined, 0);
            for (BigDecimal phaseUs : estimatedFrameSampler.phaseCandidates(refined.periodUs())) {
                FrameWindowBitSampler.SampledWindow syncSample = estimatedFrameSampler.sample(
                        edges,
                        refinedSyncStart,
                        refined.periodUs(),
                        phaseUs,
                        markerBits.size(),
                        levelMapping);
                if (!syncSample.bits().equals(markerBits)) {
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

    public List<DecodedFrame> decode(
            SignalActivitySegment segment,
            FixedPeriodBitSampler.SampledBits sampled,
            List<Integer> expectedRemoteId) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(sampled, "sampled");
        List<DecodedFrame> results = new ArrayList<>();
        int searchFrom = 0;

        while (searchFrom <= sampled.bits().size() - markerBits.size()) {
            int startBit = findMarker(sampled.bits(), searchFrom);
            if (startBit < 0) {
                break;
            }
            int availableBits = Math.min(
                    ruleSet.derived().syncInclusiveBits(), sampled.bits().size() - startBit);
            List<Integer> coreBits = List.copyOf(sampled.bits().subList(startBit, startBit + availableBits));
            List<Integer> recoveredBytes = toBytes(coreBits);
            ProtocolFrameValidator.ValidationResult validation = frameRules.validate(recoveredBytes, expectedRemoteId);
            BigDecimal startSeconds = sampled.sampleTimesSeconds().get(startBit);
            BigDecimal endSeconds = frameEnd(
                    startSeconds, availableBits, sampled.bitPeriodUs(), segment.endSeconds());

            results.add(new DecodedFrame(
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
                    ? startBit + ruleSet.derived().syncInclusiveBits()
                    : sampled.bits().size();
        }
        return List.copyOf(results);
    }

    public DecodedFrame unmatched(SignalActivitySegment segment, FixedPeriodBitSampler.SampledBits sampled) {
        List<Integer> bytes = toBytes(sampled.bits());
        BigDecimal start = sampled.sampleTimesSeconds().isEmpty()
                ? segment.startSeconds()
                : sampled.sampleTimesSeconds().get(0);
        return new DecodedFrame(
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

    public List<UnrecognizedSignalRange> boundaryFragments(
            SignalActivitySegment segment,
            List<DecodedFrame> frames) {
        if (!segment.isFileBoundarySegment()) {
            return List.of();
        }
        List<DecodedFrame> ordered = frames.stream()
                .sorted(Comparator.comparing(DecodedFrame::startSeconds))
                .toList();
        if (ordered.isEmpty()) {
            return List.of(new UnrecognizedSignalRange(
                    segment.startSeconds(),
                    segment.endSeconds(),
                    "文件边界缺少完整活动边界且未找到识别开头"));
        }

        List<UnrecognizedSignalRange> fragments = new ArrayList<>();
        DecodedFrame first = ordered.get(0);
        BigDecimal firstCoverageStart = first.audit().preludeStartSeconds() == null
                ? first.startSeconds()
                : first.audit().preludeStartSeconds();
        if (!segment.leftBoundaryKnown()
                && segment.startSeconds().compareTo(firstCoverageStart) < 0) {
            fragments.add(new UnrecognizedSignalRange(
                    segment.startSeconds(), firstCoverageStart, "文件开头残片"));
        }
        DecodedFrame last = ordered.get(ordered.size() - 1);
        if (!segment.rightBoundaryKnown()
                && last.endSeconds().compareTo(segment.endSeconds()) < 0) {
            fragments.add(new UnrecognizedSignalRange(
                    last.endSeconds(), segment.endSeconds(), "文件结尾残片"));
        }
        return List.copyOf(fragments);
    }

    private Optional<BitPeriodFit> refineMarkerFit(
            List<SignalEdge> edges,
            BitPeriodEstimator.InitialCandidate initial) {
        BigDecimal initialSyncStart = predictedTimeSeconds(initial.fit(), 0);
        BigDecimal halfPeriodSeconds = initial.fit().periodUs()
                .multiply(new BigDecimal("0.5"), MATH_CONTEXT)
                .divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        List<SignalEdge> markerEdges = new ArrayList<>(markerTransitionIndexes.size());
        List<Integer> bitIndexes = new ArrayList<>(markerTransitionIndexes.size());
        int previousEdgeIndex = initial.firstEdgeIndex() - 1;

        for (int bitIndex : markerTransitionIndexes) {
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
            List<SignalEdge> edges,
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

    private DecodedFrame recoverFormalFrame(
            List<SignalEdge> edges,
            BitPeriodEstimator.InitialCandidate initial,
            BitPeriodFit refined,
            BigDecimal syncStart,
            BigDecimal phaseUs,
            List<Integer> expectedRemoteId,
            SignalLevelMapping levelMapping) {
        BigDecimal periodSeconds = refined.periodUs().divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal formalStart = syncStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(ruleSet.derived().syncPreludeBits()), MATH_CONTEXT),
                MATH_CONTEXT);
        BigDecimal formalEnd = formalStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(ruleSet.frame().formalFrameBits()), MATH_CONTEXT),
                MATH_CONTEXT);
        FrameWindowBitSampler.SampledWindow direct = estimatedFrameSampler.sample(
                edges,
                formalStart,
                refined.periodUs(),
                phaseUs,
                ruleSet.frame().formalFrameBits(),
                levelMapping);

        TailRecovery tail = recoverTailIfAllowed(
                edges, direct, formalStart, formalEnd, refined.periodUs(), phaseUs, levelMapping);
        List<Integer> finalBits = tail.finalBits();
        List<Integer> recoveredBytes = toBytes(finalBits);
        ProtocolFrameValidator.ValidationResult validation = frameRules.validate(recoveredBytes, expectedRemoteId);
        BigDecimal phaseStepUs = refined.periodUs()
                .divide(BigDecimal.valueOf(ruleSet.timing().phaseCount()), MATH_CONTEXT);
        int lastAssociatedIndex = strictPreviousEdgeIndex(edges, formalEnd);
        BigDecimal lastAssociatedTime = lastAssociatedIndex < 0
                ? edges.get(initial.firstEdgeIndex()).timeSeconds()
                : edges.get(lastAssociatedIndex).timeSeconds();
        BigDecimal safetyRatio = direct.minimumSafetyDistanceRatio();
        if (tail.extended() && tail.syntheticSafetyRatio() != null
                && tail.syntheticSafetyRatio().compareTo(safetyRatio) < 0) {
            safetyRatio = tail.syntheticSafetyRatio();
        }

        FrameDecodeAudit audit = new FrameDecodeAudit(
                edges.get(initial.firstEdgeIndex()).timeSeconds(),
                lastAssociatedTime,
                formalStart,
                formalEnd,
                "同步前导连续1T边沿初始拟合，确认" + ruleSet.frame().syncMarkerHex()
                        + "后按已知跳变位置细化拟合并重新采样验证",
                initial.validSingleBitPulseCount(),
                initial.fit().periodUs(),
                initial.fit().maxResidualUs(),
                initial.fit().residualRatio(),
                initial.fit().periodUs().multiply(ruleSet.timing().maxFitResidualRatio()),
                refined.maxResidualUs(),
                refined.residualRatio(),
                refined.periodUs().multiply(ruleSet.timing().maxFitResidualRatio()),
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
        return new DecodedFrame(
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
            List<SignalEdge> edges,
            FrameWindowBitSampler.SampledWindow direct,
            BigDecimal formalStart,
            BigDecimal formalEnd,
            BigDecimal periodUs,
            BigDecimal phaseUs,
            SignalLevelMapping levelMapping) {
        List<Integer> finalBits = new ArrayList<>(direct.bits());
        int formalFrameBits = ruleSet.frame().formalFrameBits();
        int lastBitIndex = formalFrameBits - 1;
        if (direct.directBitCount() == formalFrameBits) {
            return new TailRecovery(
                    finalBits, false, "不适用，CSV已直接恢复104 bit",
                    null, null, null, null);
        }
        if (direct.directBitCount() != lastBitIndex || edges.size() < 2) {
            return new TailRecovery(
                    finalBits, false, "恢复失败，直接恢复不足103 bit，禁止补齐",
                    null, null, null, null);
        }

        SignalEdge previous = edges.get(edges.size() - 2);
        SignalEdge last = edges.get(edges.size() - 1);
        BigDecimal periodSeconds = periodUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT);
        BigDecimal lastBitStart = formalStart.add(
                periodSeconds.multiply(BigDecimal.valueOf(lastBitIndex), MATH_CONTEXT), MATH_CONTEXT);
        BigDecimal tailEdgeDeviationUs = last.timeSeconds().subtract(lastBitStart).abs()
                .multiply(MICROSECONDS_PER_SECOND);
        BigDecimal tailToleranceUs = periodUs.multiply(ruleSet.timing().tailEdgeToleranceRatio());
        BigDecimal previousPulseUs = last.timeSeconds().subtract(previous.timeSeconds())
                .multiply(MICROSECONDS_PER_SECOND);
        BigDecimal previousPulseErrorUs = previousPulseUs.subtract(periodUs).abs();
        BigDecimal lastSampleTime = formalStart
                .add(phaseUs.divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT), MATH_CONTEXT)
                .add(periodSeconds.multiply(BigDecimal.valueOf(lastBitIndex), MATH_CONTEXT), MATH_CONTEXT);

        boolean credible = previous.timeSeconds().compareTo(last.timeSeconds()) < 0
                && previous.level() != last.level()
                && tailEdgeDeviationUs.compareTo(tailToleranceUs) <= 0
                && localPeriodEstimator.isSingleBitPulseUs(previousPulseUs)
                && previousPulseErrorUs.compareTo(
                        periodUs.multiply(ruleSet.derived().previousPulseToleranceRatio())) <= 0
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

    private BigDecimal predictedTimeSeconds(BitPeriodFit fit, int originalBitIndex) {
        return fit.originSeconds().add(
                fit.predictedEdgeTimeUs(originalBitIndex)
                        .divide(MICROSECONDS_PER_SECOND, MATH_CONTEXT),
                MATH_CONTEXT);
    }

    private int strictPreviousEdgeIndex(List<SignalEdge> edges, BigDecimal timeSeconds) {
        int index = estimatedFrameSampler.previousEdgeIndex(edges, timeSeconds);
        while (index >= 0 && edges.get(index).timeSeconds().compareTo(timeSeconds) >= 0) {
            index--;
        }
        return index;
    }

    private int findMarker(List<Integer> bits, int fromIndex) {
        for (int index = fromIndex; index <= bits.size() - markerBits.size(); index++) {
            boolean matches = true;
            for (int markerIndex = 0; markerIndex < markerBits.size(); markerIndex++) {
                if (!bits.get(index + markerIndex).equals(markerBits.get(markerIndex))) {
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
